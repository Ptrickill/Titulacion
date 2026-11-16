import { formatNumber } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { catchError, map, Observable, of, switchMap } from 'rxjs';
import { Page } from '../../../core/http/page';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { APP_LOCALE } from '../../../core/locale';
import {
  TREATMENT_STATUS_LABELS,
  TreatmentStatus,
} from '../../../shared/clinical/treatment-labels';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatCard } from '../../../shared/ui/stat-card';
import {
  NO_REPORT_FILTERS,
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
 * `/director/reports` (HU-14, prototype 13): the operations report of the operations director. A notice that the
 * view is read only, the filters by treatment status and by physiotherapist, five indicators (active, finished and
 * abandoned treatments, sessions registered and the average per physiotherapist), the sessions of each
 * physiotherapist and the list of treatments. Every filter change reloads the indicators and the first page of the
 * list; a page change only reloads the list. Nothing here changes data, and there are no notifications. Exporting
 * the report is HU-15.
 */
@Component({
  selector: 'app-operations-report',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
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

  protected readonly pageSize = PAGE_SIZE;
  protected readonly ALL = ALL;
  protected readonly statuses: TreatmentStatus[] = ['ACTIVE', 'COMPLETED', 'ABANDONED'];
  protected readonly statusLabels = TREATMENT_STATUS_LABELS;

  protected readonly filters = new FormGroup({
    status: new FormControl<TreatmentStatus | typeof ALL>(ALL, { nonNullable: true }),
    physiotherapistId: new FormControl<number | typeof ALL>(ALL, { nonNullable: true }),
  });

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

  constructor() {
    const destroyRef = inject(DestroyRef);
    // The list of the filter is a help: without it the report still loads
    this.api
      .physiotherapists()
      .subscribe({ next: (options) => this.physiotherapists.set(options), error: () => undefined });

    this.filters.valueChanges.pipe(takeUntilDestroyed(destroyRef)).subscribe(() => {
      const { status, physiotherapistId } = this.filters.getRawValue();
      this.appliedFilters.set({
        status: status === ALL ? null : status,
        physiotherapistId: physiotherapistId === ALL ? null : physiotherapistId,
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
