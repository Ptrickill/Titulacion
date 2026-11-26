import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
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
import { Page } from '../../../core/http/page';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { personLink, PersonSummary, StaffRole } from '../../../shared/people/people-api';
import { PERSON_ROLE_LABELS, STAFF_ROLES } from '../../../shared/people/people-labels';
import { openPersonFormDialog } from '../../../shared/people/person-form-dialog';
import { PeopleTable } from '../../../shared/people/people-table';
import { StaffApi, StaffFilter, StaffStats } from '../../../shared/people/staff-api';
import { AccountStatus } from '../../../shared/people/user-api';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatCard } from '../../../shared/ui/stat-card';

const PAGE_SIZE = 10;

interface SearchState extends StaffFilter {
  page: number;
}

/**
 * "Gestión de Personal" of the operations director (MEJ-05): the physiotherapists, the reception and the operations
 * direction with their accounts. Four indicators (active accounts and the accounts of each role), the search, the
 * filters by role and by the status of the account, and the shared table of people with the account actions (never
 * the director's own deactivation; the API also keeps one active director). "Registrar personal" opens
 * {@link PersonFormDialog} on the role step. The record of each member, with the physiotherapists' schedule, is at
 * /director/staff/:id.
 */
@Component({
  selector: 'app-staff-list',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    PageHeader,
    StatCard,
    EmptyState,
    PeopleTable,
  ],
  templateUrl: './staff-list.html',
  styleUrl: './staff-list.scss',
})
export class StaffList {
  private readonly api = inject(StaffApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly staffRoles = STAFF_ROLES;
  protected readonly roleLabels = PERSON_ROLE_LABELS;

  /** Filters and page of the shown results; every new value loads them. */
  protected readonly search = signal<SearchState>({
    query: '',
    role: null,
    accountStatus: null,
    page: 0,
  });
  protected readonly stats = signal<StaffStats | null>(null);
  protected readonly results = signal<Page<PersonSummary> | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  /** Staff member whose data is being fetched to open the edit form. */
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
  protected onRoleChange(role: StaffRole | ''): void {
    this.search.update((state) => ({ ...state, role: role || null, page: 0 }));
  }

  protected onAccountStatusChange(accountStatus: AccountStatus | ''): void {
    this.search.update((state) => ({ ...state, accountStatus: accountStatus || null, page: 0 }));
  }

  protected goToPage(page: number): void {
    this.search.update((state) => ({ ...state, page }));
  }

  /** Loads the same filters and page again (a new object, so the search signal emits). */
  protected reload(): void {
    this.search.update((state) => ({ ...state }));
  }

  /** The indicators do not block the list: without them the cards stay empty. */
  protected loadStats(): void {
    this.api.stats().subscribe({ next: (stats) => this.stats.set(stats), error: () => undefined });
  }

  protected openRegisterDialog(): void {
    openPersonFormDialog(this.dialog, { type: 'STAFF' }).subscribe((result) => {
      if (result?.type !== 'STAFF') {
        return;
      }
      this.snackBar.open(
        `Personal registrado. Enviamos un enlace de activación a ${result.staff.email}`,
        'Cerrar',
        { duration: 6000 },
      );
      void this.router.navigate(personLink('STAFF', result.staff.id));
    });
  }

  /** Fetches the full personal data, then opens the form in edit mode. */
  protected edit(person: PersonSummary): void {
    if (this.openingKey() !== null) {
      return;
    }
    this.openingKey.set(`${person.type}:${person.id}`);
    this.api
      .findById(person.id)
      .pipe(
        finalize(() => this.openingKey.set(null)),
        switchMap((staff) => openPersonFormDialog(this.dialog, { staff })),
      )
      .subscribe({
        next: (saved) => {
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
