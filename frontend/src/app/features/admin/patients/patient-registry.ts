import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  finalize,
  map,
  Observable,
  of,
  Subject,
  switchMap,
} from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { DataTable, DataTableColumn } from '../../../shared/ui/data-table/data-table';
import { DataTableCell } from '../../../shared/ui/data-table/data-table-cell';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { formatAge } from './patient-age';
import { Page } from '../../../core/http/page';
import { PatientApi, PatientSummary } from './patient-api';
import { openPatientFormDialog } from './patient-form-dialog';

const PAGE_SIZE = 10;

interface SearchState {
  query: string;
  page: number;
}

/**
 * "Registro y Control de Pacientes" (prototype 10): registers patients (HU-05), searches them by name or national
 * ID with server-side pagination and edits their data (HU-06). Only identification and contact data are shown;
 * the prototype's session and treatment status columns are clinical data, which the administrator never sees.
 */
@Component({
  selector: 'app-patient-registry',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    PageHeader,
    DataTable,
    DataTableCell,
    EmptyState,
  ],
  templateUrl: './patient-registry.html',
  styleUrl: './patient-registry.scss',
})
export class PatientRegistry {
  private readonly api = inject(PatientApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly formatAge = formatAge;
  protected readonly columns: DataTableColumn<PatientSummary>[] = [
    { key: 'lastName', header: 'Paciente' },
    { key: 'phone', header: 'Contacto' },
    { key: 'id', header: 'Acciones', align: 'end' },
  ];

  /** Query and page of the shown results; every new value loads them. */
  protected readonly search = signal<SearchState>({ query: '', page: 0 });
  protected readonly results = signal<Page<PatientSummary> | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  /** Patient whose data is being fetched to open the edit form. */
  protected readonly openingId = signal<number | null>(null);

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

  /** Loads the same query and page again (a new object, so the search signal emits). */
  protected reload(): void {
    this.search.update((state) => ({ ...state }));
  }

  protected initials(patient: PatientSummary): string {
    return `${patient.firstName.charAt(0)}${patient.lastName.charAt(0)}`.toUpperCase();
  }

  protected openRegisterDialog(): void {
    openPatientFormDialog(this.dialog).subscribe((patient) => {
      if (patient) {
        this.snackBar.open('Paciente registrado correctamente.', 'Cerrar', { duration: 5000 });
        void this.router.navigate(['/admin/patients', patient.id]);
      }
    });
  }

  /** Fetches the full personal data, then opens the HU-05 form in edit mode. */
  protected edit(summary: PatientSummary): void {
    if (this.openingId() !== null) {
      return;
    }
    this.openingId.set(summary.id);
    this.api
      .findById(summary.id)
      .pipe(
        finalize(() => this.openingId.set(null)),
        switchMap((patient) => openPatientFormDialog(this.dialog, patient)),
      )
      .subscribe({
        next: (saved) => {
          if (saved) {
            this.snackBar.open('Datos del paciente actualizados correctamente.', 'Cerrar', {
              duration: 5000,
            });
            this.reload();
          }
        },
        error: (error: unknown) =>
          this.snackBar.open(
            problemDetailMessage(error, 'No se pudieron cargar los datos del paciente.'),
            'Cerrar',
            { duration: 6000 },
          ),
      });
  }

  private load(state: SearchState): Observable<Page<PatientSummary> | null> {
    this.loading.set(true);
    return this.api.search(state.query, state.page, PAGE_SIZE).pipe(catchError(() => of(null)));
  }
}
