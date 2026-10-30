import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  Observable,
  of,
  Subject,
  switchMap,
} from 'rxjs';
import { Page } from '../../../core/http/page';
import { DataTable, DataTableColumn } from '../../../shared/ui/data-table/data-table';
import { DataTableCell } from '../../../shared/ui/data-table/data-table-cell';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { formatAge } from '../../admin/patients/patient-age';
import {
  SEVERITY_LABELS,
  SEVERITY_TONES,
  TREATMENT_STATUS_LABELS,
  TREATMENT_STATUS_TONES,
} from '../clinical-records/clinical-labels';
import { ClinicalRecordApi, PhysioPatient } from '../clinical-records/clinical-record-api';

const PAGE_SIZE = 10;

interface SearchState {
  query: string;
  page: number;
}

/**
 * "Gestión de Pacientes" of the physiotherapist (HU-07, prototype 03): patients with the summary of their current
 * treatment, searched by name or national ID and paginated in the server. Patients without a clinical record offer
 * "Crear historial"; records without an active treatment offer "Nuevo tratamiento".
 */
@Component({
  selector: 'app-physio-patient-list',
  imports: [
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    PageHeader,
    DataTable,
    DataTableCell,
    EmptyState,
    StatusBadge,
  ],
  templateUrl: './physio-patient-list.html',
  styleUrl: './physio-patient-list.scss',
})
export class PhysioPatientList {
  private readonly api = inject(ClinicalRecordApi);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly formatAge = formatAge;
  protected readonly severityLabels = SEVERITY_LABELS;
  protected readonly severityTones = SEVERITY_TONES;
  protected readonly statusLabels = TREATMENT_STATUS_LABELS;
  protected readonly statusTones = TREATMENT_STATUS_TONES;

  /** Every column has its own cell template; the keys only name them. */
  protected readonly columns: DataTableColumn<PhysioPatient>[] = [
    { key: 'lastName', header: 'Paciente' },
    { key: 'currentTreatment', header: 'Diagnóstico principal' },
    { key: 'nationalId', header: 'Sesiones', align: 'center' },
    { key: 'age', header: 'Estado' },
    { key: 'id', header: 'Acciones', align: 'end' },
  ];

  protected readonly search = signal<SearchState>({ query: '', page: 0 });
  protected readonly results = signal<Page<PhysioPatient> | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);

  private readonly typed = new Subject<string>();

  constructor() {
    const destroyRef = inject(DestroyRef);
    // A new query starts again from the first page
    this.typed
      .pipe(
        map((query) => query.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((query) => this.search.set({ query, page: 0 }));

    toObservable(this.search)
      .pipe(
        switchMap((state) => this.load(state)),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((page) => {
        this.loading.set(false);
        this.loadError.set(page === null);
        if (page) {
          this.results.set(page);
        }
      });
  }

  protected onSearch(event: Event): void {
    this.typed.next((event.target as HTMLInputElement).value);
  }

  protected goToPage(page: number): void {
    this.search.update((state) => ({ ...state, page }));
  }

  protected reload(): void {
    this.search.update((state) => ({ ...state }));
  }

  protected initials(patient: PhysioPatient): string {
    return `${patient.firstName.charAt(0)}${patient.lastName.charAt(0)}`.toUpperCase();
  }

  private load(state: SearchState): Observable<Page<PhysioPatient> | null> {
    this.loading.set(true);
    return this.api
      .listPatients(state.query, state.page, PAGE_SIZE)
      .pipe(catchError(() => of(null)));
  }
}
