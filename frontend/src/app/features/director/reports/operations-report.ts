import { formatDate, formatNumber } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { catchError, map, Observable, of, switchMap } from 'rxjs';
import { FileDownload } from '../../../core/http/file-download';
import { Page } from '../../../core/http/page';
import { blobProblemDetailMessage, problemDetailMessage } from '../../../core/http/problem-detail';
import { APP_LOCALE } from '../../../core/locale';
import {
  TREATMENT_STATUS_LABELS,
  TreatmentStatus,
} from '../../../shared/clinical/treatment-labels';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatCard } from '../../../shared/ui/stat-card';
import { DATE_RANGE_MESSAGE, dateRangeValidator } from '../../../shared/validation/date-range';
import {
  NO_REPORT_FILTERS,
  periodLabel,
  PhysiotherapistOption,
  ReportApi,
  ReportFilters,
  ReportSummary,
  ReportTreatment,
} from './report-api';
import { ReportTreatmentList } from './report-treatment-list';
import { SessionsByPhysiotherapistCard } from './sessions-by-physiotherapist-card';

/** Five treatments per page, so the list is about as tall as the card of the sessions beside it. */
const PAGE_SIZE = 5;
const CONNECTION_ERROR = 'Revise su conexión e intente nuevamente.';
const EXPORT_ERROR = 'No se pudo exportar el informe. Intente nuevamente.';
/** The name of the file carries the date in Quito (UTC-5 all year), wherever the browser is. */
const QUITO_OFFSET = '-0500';
/**
 * Value of the "Todos" options. Not null: mat-select shows nothing for a null value, and the option must read
 * "Todos los estados" or "Todos los fisioterapeutas".
 */
const ALL = '';

/** Every request carries the attempt, so "Reintentar" asks again with the same filters and page. */
interface SummaryQuery {
  filters: ReportFilters;
  attempt: number;
}

interface TreatmentsQuery extends SummaryQuery {
  page: number;
}

/**
 * `/director/reports` (HU-14, HU-15, prototype 13): the operations report of the operations director. A notice that
 * the view is read only, the filters by treatment status, by physiotherapist and by date range, five indicators
 * (active, finished and abandoned treatments, sessions registered and the average per physiotherapist), the sessions
 * of each physiotherapist and the list of treatments. Every filter change reloads the indicators and the first page of
 * the list; a page change only reloads the list; a range whose start is after its end searches nothing. "Exportar
 * informe operativo" downloads the PDF with the filters the page shows. Nothing here changes data, and there are no
 * notifications.
 */
@Component({
  selector: 'app-operations-report',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    PageHeader,
    EmptyState,
    StatCard,
    SessionsByPhysiotherapistCard,
    ReportTreatmentList,
  ],
  templateUrl: './operations-report.html',
  styleUrl: './operations-report.scss',
})
export class OperationsReport {
  private readonly api = inject(ReportApi);
  private readonly fileDownload = inject(FileDownload);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly ALL = ALL;
  protected readonly statuses: TreatmentStatus[] = ['ACTIVE', 'COMPLETED', 'ABANDONED'];
  protected readonly statusLabels = TREATMENT_STATUS_LABELS;
  protected readonly rangeMessage = DATE_RANGE_MESSAGE;

  /** The dates are yyyy-MM-dd, '' when empty (an empty date input). */
  protected readonly filters = new FormGroup(
    {
      status: new FormControl<TreatmentStatus | typeof ALL>(ALL, { nonNullable: true }),
      physiotherapistId: new FormControl<number | typeof ALL>(ALL, { nonNullable: true }),
      from: new FormControl('', { nonNullable: true }),
      to: new FormControl('', { nonNullable: true }),
    },
    { validators: dateRangeValidator },
  );

  /** "Desde" after "Hasta": nothing is searched nor exported until it is fixed. */
  protected readonly invalidRange = signal(false);
  /** The dates typed, to offer clearing each one. */
  protected readonly dates = signal({ from: '', to: '' });
  protected readonly exporting = signal(false);
  protected readonly exportError = signal<string | null>(null);

  protected readonly physiotherapists = signal<PhysiotherapistOption[]>([]);
  private readonly appliedFilters = signal<ReportFilters>(NO_REPORT_FILTERS);
  private readonly page = signal(0);
  private readonly attempt = signal(0);

  protected readonly summary = signal<ReportSummary | null>(null);
  protected readonly treatments = signal<Page<ReportTreatment> | null>(null);
  private readonly summaryLoading = signal(true);
  private readonly treatmentsLoading = signal(true);
  private readonly summaryError = signal<string | null>(null);
  private readonly treatmentsError = signal<string | null>(null);

  protected readonly loading = computed(() => this.summaryLoading() || this.treatmentsLoading());
  protected readonly loadError = computed(() => this.summaryError() ?? this.treatmentsError());

  protected readonly average = computed(() => {
    const summary = this.summary();
    return summary
      ? formatNumber(summary.averageSessionsPerPhysiotherapist, APP_LOCALE, '1.1-1')
      : '';
  });

  /** Footer of "Sesiones registradas": the sessions of each status. */
  protected readonly sessionsByStatus = computed(() => {
    const sessions = this.summary()?.sessions;
    if (!sessions) {
      return '';
    }
    const completed = `${sessions.completed} ${sessions.completed === 1 ? 'realizada' : 'realizadas'}`;
    const rescheduled = `${sessions.rescheduled} ${
      sessions.rescheduled === 1 ? 'reprogramada' : 'reprogramadas'
    }`;
    return `${completed} · ${rescheduled} · ${sessions.noShow} no asistió`;
  });

  protected readonly statusFilterLabel = computed(() => {
    const status = this.appliedFilters().status;
    return status ? TREATMENT_STATUS_LABELS[status] : 'Todos';
  });

  protected readonly physiotherapistFilterLabel = computed(() => {
    const id = this.appliedFilters().physiotherapistId;
    if (id === null) {
      return 'Todos';
    }
    return this.physiotherapists().find((option) => option.id === id)?.fullName ?? 'Seleccionado';
  });

  /** Label of the applied range, or null without one. */
  protected readonly periodFilterLabel = computed(() => periodLabel(this.appliedFilters()));

  /** The PDF has the filters the page shows, so it waits for them to load and for a valid range. */
  protected readonly canExport = computed(
    () => !this.exporting() && !this.invalidRange() && !this.loading(),
  );

  constructor() {
    const destroyRef = this.destroyRef;
    // The list of the filter is a help: without it the report still loads
    this.api
      .physiotherapists()
      .subscribe({ next: (options) => this.physiotherapists.set(options), error: () => undefined });

    this.filters.valueChanges.pipe(takeUntilDestroyed(destroyRef)).subscribe(() => {
      const { status, physiotherapistId, from, to } = this.filters.getRawValue();
      this.dates.set({ from, to });
      this.invalidRange.set(this.filters.hasError('range'));
      if (this.invalidRange()) {
        return;
      }
      this.appliedFilters.set({
        status: status === ALL ? null : status,
        physiotherapistId: physiotherapistId === ALL ? null : physiotherapistId,
        from: from || null,
        to: to || null,
      });
      this.page.set(0);
    });

    const summaryQuery = computed<SummaryQuery>(() => ({
      filters: this.appliedFilters(),
      attempt: this.attempt(),
    }));
    toObservable(summaryQuery)
      .pipe(
        switchMap((query) => this.loadSummary(query)),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((result) => {
        this.summaryLoading.set(false);
        if (typeof result === 'string') {
          this.summaryError.set(result);
          return;
        }
        this.summaryError.set(null);
        this.summary.set(result);
      });

    const treatmentsQuery = computed<TreatmentsQuery>(() => ({
      filters: this.appliedFilters(),
      page: this.page(),
      attempt: this.attempt(),
    }));
    toObservable(treatmentsQuery)
      .pipe(
        switchMap((query) => this.loadTreatments(query)),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((result) => {
        this.treatmentsLoading.set(false);
        if (typeof result === 'string') {
          this.treatmentsError.set(result);
          return;
        }
        this.treatmentsError.set(null);
        this.treatments.set(result);
      });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
  }

  /** Asks again for the indicators and the current page, with the same filters. */
  protected reload(): void {
    this.attempt.update((attempt) => attempt + 1);
  }

  protected clearDate(control: 'from' | 'to'): void {
    this.filters.controls[control].setValue('');
  }

  /**
   * Downloads the PDF of the report with the applied filters: the same ones the indicators and the list show. The
   * server audits it as an export.
   */
  protected exportReport(): void {
    if (!this.canExport()) {
      return;
    }
    this.exporting.set(true);
    this.exportError.set(null);
    this.api
      .exportPdf(this.appliedFilters())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (pdf) => {
          this.exporting.set(false);
          this.fileDownload.save(pdf, reportFileName());
        },
        error: (error: unknown) => {
          void blobProblemDetailMessage(error, EXPORT_ERROR).then((message) => {
            this.exporting.set(false);
            this.exportError.set(message);
          });
        },
      });
  }

  /** The indicators, or the message of the error. */
  private loadSummary(query: SummaryQuery): Observable<ReportSummary | string> {
    this.summaryLoading.set(true);
    return this.api.summary(query.filters).pipe(
      map((summary): ReportSummary | string => summary),
      catchError((error: unknown) => of(problemDetailMessage(error, CONNECTION_ERROR))),
    );
  }

  /** One page of treatments, or the message of the error. */
  private loadTreatments(query: TreatmentsQuery): Observable<Page<ReportTreatment> | string> {
    this.treatmentsLoading.set(true);
    return this.api.treatments(query.filters, query.page, PAGE_SIZE).pipe(
      map((page): Page<ReportTreatment> | string => page),
      catchError((error: unknown) => of(problemDetailMessage(error, CONNECTION_ERROR))),
    );
  }
}

/** "informe-operativo-2026-11-16.pdf", the same name the server gives it. */
function reportFileName(): string {
  return `informe-operativo-${formatDate(Date.now(), 'yyyy-MM-dd', APP_LOCALE, QUITO_OFFSET)}.pdf`;
}
