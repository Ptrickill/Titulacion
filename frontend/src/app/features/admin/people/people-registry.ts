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
  PeopleApi,
  PeopleFilter,
  PeopleStats,
  personLink,
  PersonAccount,
  PersonAccountStatus,
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
import { AccountActions } from './account-actions';
import { formatLastAccess } from './last-access';
import { AuthService } from '../../../core/auth/auth-service';
import { StatCard } from '../../../shared/ui/stat-card';
import { MatTooltipModule } from '@angular/material/tooltip';

const PAGE_SIZE = 10;

interface SearchState extends PeopleFilter {
  page: number;
}

/**
 * "Registro de Usuarios" (HU-02, HU-03, HU-05, HU-06, MEJ-02): the one page of the administrator for people and
 * their accounts. Four indicators (active accounts, physiotherapists, patients with portal and administrators), and
 * patients and staff in one list, staff first, with the filters by type, role and status of the account ("Sin
 * cuenta", "Pendiente de activación", "Activa", "Inactiva"). Each row shows the account with its last access and the
 * actions that "Gestión de Usuarios" had: resend the pending link and activate or deactivate (never the
 * administrator's own account), besides "Editar". "Registrar usuario" opens {@link PersonFormDialog}. Only
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
    MatTooltipModule,
    PageHeader,
    StatCard,
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
  private readonly accounts = inject(AccountActions);
  private readonly auth = inject(AuthService);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly staffRoles = STAFF_ROLES;
  protected readonly roleLabels = PERSON_ROLE_LABELS;
  protected readonly roleTones = PERSON_ROLE_TONES;
  protected readonly accountBadges = ACCOUNT_STATUS_BADGES;
  protected readonly link = personLink;
  protected readonly formatLastAccess = formatLastAccess;
  protected readonly columns: DataTableColumn<PersonSummary>[] = [
    // The role goes under the name: the contact column needs the width for long emails
    { key: 'lastName', header: 'Persona y rol' },
    { key: 'phone', header: 'Contacto' },
    { key: 'accountStatus', header: 'Cuenta' },
    { key: 'id', header: 'Acciones', align: 'end' },
  ];

  /** Filters and page of the shown results; every new value loads them. */
  protected readonly search = signal<SearchState>({
    query: '',
    type: null,
    role: null,
    accountStatus: null,
    page: 0,
  });
  protected readonly stats = signal<PeopleStats | null>(null);
  /** Rows whose account action is in flight; their buttons stay disabled. */
  protected readonly pendingKeys = signal<ReadonlySet<string>>(new Set());
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

    this.loadStats();
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

  protected onAccountStatusChange(accountStatus: PersonAccountStatus | ''): void {
    this.search.update((state) => ({ ...state, accountStatus: accountStatus || null, page: 0 }));
  }

  /** The logged-in administrator cannot deactivate the own account. */
  protected isOwnAccount(person: PersonSummary): boolean {
    return person.userId !== null && person.userId === this.auth.user()?.id;
  }

  /** Resends the activation link of a pending account, or the confirmation of a new email. */
  protected resend(person: PersonSummary): void {
    this.runAccountAction(person, (account) => this.accounts.resendOf(account, person.email));
  }

  /** Activates or deactivates the account, after a confirmation. */
  protected toggleStatus(person: PersonSummary): void {
    if (this.isOwnAccount(person)) {
      return;
    }
    this.runAccountAction(person, (account) =>
      this.accounts.toggleStatusOf(account, `${person.firstName} ${person.lastName}`),
    );
  }

  private runAccountAction(
    person: PersonSummary,
    action: (account: PersonAccount) => Observable<PersonAccount>,
  ): void {
    const key = this.keyOf(person);
    if (person.userId === null || this.pendingKeys().has(key)) {
      return;
    }
    const account: PersonAccount = {
      userId: person.userId,
      status: person.accountStatus as PersonAccount['status'],
      emailChangePending: person.emailChangePending,
      lastLoginAt: person.lastLoginAt,
    };
    this.setPending(key, true);
    action(account)
      .pipe(finalize(() => this.setPending(key, false)))
      .subscribe((updated) => {
        this.results.update((page) =>
          page
            ? {
                ...page,
                content: page.content.map((row) =>
                  this.keyOf(row) === key
                    ? {
                        ...row,
                        accountStatus: updated.status,
                        emailChangePending: updated.emailChangePending,
                        lastLoginAt: updated.lastLoginAt,
                      }
                    : row,
                ),
              }
            : page,
        );
        this.loadStats();
      });
  }

  private setPending(key: string, pending: boolean): void {
    this.pendingKeys.update((keys) => {
      const next = new Set(keys);
      if (pending) {
        next.add(key);
      } else {
        next.delete(key);
      }
      return next;
    });
  }

  /** The indicators do not block the list: without them the cards stay empty. */
  private loadStats(): void {
    this.api.stats().subscribe({ next: (stats) => this.stats.set(stats), error: () => undefined });
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
