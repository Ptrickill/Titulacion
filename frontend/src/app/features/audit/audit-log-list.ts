import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { catchError, map, Observable, of, switchMap } from 'rxjs';
import { Page } from '../../core/http/page';
import { problemDetailMessage } from '../../core/http/problem-detail';
import { DataTable, DataTableColumn } from '../../shared/ui/data-table/data-table';
import { DataTableCell } from '../../shared/ui/data-table/data-table-cell';
import { EmptyState } from '../../shared/ui/empty-state';
import { PageHeader } from '../../shared/ui/page-header';
import { StatusBadge } from '../../shared/ui/status-badge';
import {
  AUDIT_ACTION_LABELS,
  AUDIT_ACTION_TONES,
  AUDIT_ACTIONS,
  entityLabel,
} from './audit-labels';
import {
  AuditAction,
  AuditLogApi,
  AuditLogEntry,
  AuditLogFilters,
  AuditUser,
  NO_FILTERS,
} from './audit-log-api';

const PAGE_SIZE = 20;

interface AuditQuery {
  filters: AuditLogFilters;
  page: number;
}

/** "Desde" after "Hasta" is an error of the whole group; the dates are yyyy-MM-dd, so they compare as text. */
function dateRange(group: AbstractControl): ValidationErrors | null {
  const { from, to } = (group as FormGroup).getRawValue() as AuditLogFilters;
  return from && to && from > to ? { range: true } : null;
}

/**
 * Audit log (HU-18), read-only, for the administrator (`/admin/audit`) and the operations director
 * (`/director/audit`): who created, changed or read clinical data, plus denied accesses and logins. Only metadata is
 * shown (user, action, entity and id, date and IP), never clinical data. Every filter change reloads the first page.
 */
@Component({
  selector: 'app-audit-log-list',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    PageHeader,
    DataTable,
    DataTableCell,
    EmptyState,
    StatusBadge,
  ],
  templateUrl: './audit-log-list.html',
  styleUrl: './audit-log-list.scss',
})
export class AuditLogList {
  private readonly api = inject(AuditLogApi);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly actions = AUDIT_ACTIONS;
  protected readonly actionLabels = AUDIT_ACTION_LABELS;
  protected readonly actionTones = AUDIT_ACTION_TONES;
  protected readonly entityLabel = entityLabel;
  /** Dates and times are shown in Quito (UTC-5 all year), wherever the browser is. */
  protected readonly quitoOffset = '-0500';
  protected readonly columns: DataTableColumn<AuditLogEntry>[] = [
    { key: 'occurredAt', header: 'Fecha y hora' },
    { key: 'userName', header: 'Usuario' },
    { key: 'action', header: 'Acción' },
    { key: 'entityType', header: 'Entidad' },
    { key: 'ipAddress', header: 'IP' },
  ];

  protected readonly filters = new FormGroup(
    {
      userId: new FormControl<number | null>(null),
      action: new FormControl<AuditAction | null>(null),
      entityType: new FormControl<string | null>(null),
      from: new FormControl<string | null>(null),
      to: new FormControl<string | null>(null),
    },
    { validators: dateRange },
  );

  protected readonly users = signal<AuditUser[]>([]);
  protected readonly entityTypes = signal<string[]>([]);
  protected readonly query = signal<AuditQuery>({ filters: NO_FILTERS, page: 0 });
  protected readonly results = signal<Page<AuditLogEntry> | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  constructor() {
    const destroyRef = inject(DestroyRef);
    // The filter lists are a help: without them the log still loads
    this.api.users().subscribe({ next: (users) => this.users.set(users), error: () => undefined });
    this.api
      .entityTypes()
      .subscribe({ next: (types) => this.entityTypes.set(types), error: () => undefined });

    this.filters.valueChanges.pipe(takeUntilDestroyed(destroyRef)).subscribe(() => {
      if (this.filters.valid) {
        this.query.set({ filters: this.currentFilters(), page: 0 });
      }
    });

    toObservable(this.query)
      .pipe(
        switchMap((query) => this.load(query)),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (typeof result === 'string') {
          this.loadError.set(result);
          return;
        }
        this.loadError.set(null);
        this.results.set(result);
      });
  }

  protected goToPage(page: number): void {
    this.query.update((query) => ({ ...query, page }));
  }

  /** Loads the same filters and page again (a new object, so the query signal emits). */
  protected reload(): void {
    this.query.update((query) => ({ ...query }));
  }

  protected clear(): void {
    this.filters.reset();
  }

  private currentFilters(): AuditLogFilters {
    const value = this.filters.getRawValue();
    return {
      userId: value.userId,
      action: value.action,
      entityType: value.entityType,
      from: value.from || null,
      to: value.to || null,
    };
  }

  /** A page, or the message of the error. */
  private load(query: AuditQuery): Observable<Page<AuditLogEntry> | string> {
    this.loading.set(true);
    return this.api.search(query.filters, query.page, PAGE_SIZE).pipe(
      map((page): Page<AuditLogEntry> | string => page),
      catchError((error: unknown) =>
        of(problemDetailMessage(error, 'Revise su conexión e intente nuevamente.')),
      ),
    );
  }
}
