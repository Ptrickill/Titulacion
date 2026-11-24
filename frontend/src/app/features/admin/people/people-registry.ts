import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
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
import { Page } from '../../../core/http/page';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { DataTable, DataTableColumn } from '../../../shared/ui/data-table/data-table';
import { DataTableCell } from '../../../shared/ui/data-table/data-table-cell';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { PatientApi } from '../patients/patient-api';
import {
  OPEN_REGISTER_STATE,
  PeopleApi,
  PeopleFilter,
  personLink,
  PersonSummary,
  PersonType,
  StaffRole,
} from './people-api';
import {
  ACCOUNT_STATUS_BADGES,
  PERSON_ROLE_LABELS,
  PERSON_ROLE_TONES,
  STAFF_ROLES,
} from './people-labels';
import { openPersonFormDialog, PersonFormDialogData, PersonFormResult } from './person-form-dialog';
import { StaffApi } from './staff-api';

const PAGE_SIZE = 10;

interface SearchState extends PeopleFilter {
  page: number;
}

/**
 * "Registro de Usuarios" (HU-05, HU-06, MEJ-02): patients and staff in one list, staff first, with the filters by
 * type and role and the status of each person's account ("Sin cuenta", "Pendiente de activación", "Activa",
 * "Inactiva"). "Registrar usuario" opens {@link PersonFormDialog}, which asks first for the type of person. Only
 * identification and contact data are shown, never clinical data.
 */
@Component({
  selector: 'app-people-registry',
  imports: [
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    PageHeader,
    StatusBadge,
    DataTable,
    DataTableCell,
    EmptyState,
  ],
  templateUrl: './people-registry.html',
  styleUrl: './people-registry.scss',
})
export class PeopleRegistry {
  private readonly api = inject(PeopleApi);
  private readonly patientApi = inject(PatientApi);
  private readonly staffApi = inject(StaffApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly staffRoles = STAFF_ROLES;
  protected readonly roleLabels = PERSON_ROLE_LABELS;
  protected readonly roleTones = PERSON_ROLE_TONES;
  protected readonly accountBadges = ACCOUNT_STATUS_BADGES;
  protected readonly link = personLink;
  protected readonly columns: DataTableColumn<PersonSummary>[] = [
    // The role goes under the name: the contact column needs the width for long emails
    { key: 'lastName', header: 'Persona y rol' },
    { key: 'phone', header: 'Contacto' },
    { key: 'accountStatus', header: 'Cuenta' },
    { key: 'id', header: 'Acciones', align: 'end' },
  ];

  /** Filters and page of the shown results; every new value loads them. */
  protected readonly search = signal<SearchState>({ query: '', type: null, role: null, page: 0 });
  protected readonly results = signal<Page<PersonSummary> | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  /** Person whose data is being fetched to open the edit form. */
  protected readonly openingKey = signal<string | null>(null);

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
      .subscribe((query) => this.search.update((state) => ({ ...state, query, page: 0 })));

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

    // "Crear usuario" in "Gestión de Usuarios" brings here with the dialog to open, without a URL parameter
    const state = this.router.currentNavigation()?.extras.state ?? history.state;
    if ((state as Record<string, unknown> | null)?.[OPEN_REGISTER_STATE] === true) {
      queueMicrotask(() => this.openRegisterDialog());
    }
  }

  protected onSearch(event: Event): void {
    this.typed.next((event.target as HTMLInputElement).value);
  }

  /** '' is "Todos": mat-select does not show the text of an option whose value is null. */
  protected onTypeChange(type: PersonType | ''): void {
    this.search.update((state) => ({
      ...state,
      type: type || null,
      role: type === 'STAFF' ? state.role : null,
      page: 0,
    }));
  }

  protected onRoleChange(role: StaffRole | ''): void {
    this.search.update((state) => ({ ...state, role: role || null, page: 0 }));
  }

  protected goToPage(page: number): void {
    this.search.update((state) => ({ ...state, page }));
  }

  /** Loads the same filters and page again (a new object, so the search signal emits). */
  protected reload(): void {
    this.search.update((state) => ({ ...state }));
  }

  protected initials(person: PersonSummary): string {
    return `${person.firstName.charAt(0)}${person.lastName.charAt(0)}`.toUpperCase();
  }

  /** The email split before the at sign, so a long one wraps there and not letter by letter. */
  protected emailParts(email: string): [string, string] {
    const at = email.indexOf('@');
    return at > 0 ? [email.slice(0, at), email.slice(at)] : [email, ''];
  }

  protected keyOf(person: PersonSummary): string {
    return `${person.type}:${person.id}`;
  }

  protected openRegisterDialog(): void {
    openPersonFormDialog(this.dialog).subscribe((result) => {
      if (!result) {
        return;
      }
      if (result.type === 'STAFF') {
        this.snackBar.open(
          `Usuario registrado. Enviamos un enlace de activación a ${result.staff.email}`,
          'Cerrar',
          { duration: 6000 },
        );
        void this.router.navigate(personLink('STAFF', result.staff.id));
      } else {
        this.snackBar.open('Paciente registrado correctamente.', 'Cerrar', { duration: 5000 });
        void this.router.navigate(personLink('PATIENT', result.patient.id));
      }
    });
  }

  /** Fetches the full personal data, then opens the form in edit mode. */
  protected edit(person: PersonSummary): void {
    if (this.openingKey() !== null) {
      return;
    }
    this.openingKey.set(this.keyOf(person));
    const data: Observable<PersonFormDialogData> =
      person.type === 'PATIENT'
        ? this.patientApi.findById(person.id).pipe(map((patient) => ({ patient })))
        : this.staffApi.findById(person.id).pipe(map((staff) => ({ staff })));
    data
      .pipe(
        finalize(() => this.openingKey.set(null)),
        switchMap((dialogData) => openPersonFormDialog(this.dialog, dialogData)),
      )
      .subscribe({
        next: (saved: PersonFormResult | undefined) => {
          if (saved) {
            this.snackBar.open('Datos actualizados correctamente.', 'Cerrar', { duration: 5000 });
            this.reload();
          }
        },
        error: (error: unknown) =>
          this.snackBar.open(
            problemDetailMessage(error, 'No se pudieron cargar los datos de la persona.'),
            'Cerrar',
            { duration: 6000 },
          ),
      });
  }

  private load(state: SearchState): Observable<Page<PersonSummary> | null> {
    this.loading.set(true);
    return this.api.list(state, state.page, PAGE_SIZE).pipe(catchError(() => of(null)));
  }
}
