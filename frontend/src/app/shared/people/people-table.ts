import { Component, computed, inject, input, linkedSignal, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { finalize, Observable } from 'rxjs';
import { AuthService } from '../../core/auth/auth-service';
import { Page } from '../../core/http/page';
import { DataTable, DataTableColumn } from '../ui/data-table/data-table';
import { DataTableCell } from '../ui/data-table/data-table-cell';
import { StatusBadge } from '../ui/status-badge';
import { AccountActions } from './account-actions';
import { formatLastAccess } from './last-access';
import { PersonAccount, personLink, PersonSummary } from './people-api';
import { ACCOUNT_STATUS_BADGES, PERSON_ROLE_LABELS, PERSON_ROLE_TONES } from './people-labels';

/**
 * The table of people of the reception's "Registro de Pacientes" and of the operations director's "Gestión de
 * Personal" (MEJ-05): person and role (a link to the record), contact, account (status, "Correo por confirmar" and
 * the last access) and the actions: resend the pending link, activate or deactivate the account (never the user's
 * own) and "Editar". Each side only lists its own people, so the API accepts every action shown here. Paginated on the
 * server by the page that owns it; only identification and contact data, never clinical data.
 */
@Component({
  selector: 'app-people-table',
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    StatusBadge,
    DataTable,
    DataTableCell,
  ],
  templateUrl: './people-table.html',
  styleUrl: './people-table.scss',
})
export class PeopleTable {
  private readonly accounts = inject(AccountActions);
  private readonly auth = inject(AuthService);

  readonly page = input.required<Page<PersonSummary>>();
  readonly pageSize = input.required<number>();
  readonly pageIndex = input.required<number>();
  readonly caption = input.required<string>();
  readonly itemLabel = input.required<string>();
  readonly emptyMessage = input.required<string>();
  /** Person whose data the page is fetching to open the edit form: its button shows a spinner. */
  readonly openingKey = input<string | null>(null);

  readonly pageIndexChange = output<number>();
  readonly edit = output<PersonSummary>();
  /** An account changed (status or pending link): the page reloads its indicators. */
  readonly accountChanged = output<void>();

  /** The rows of the page, updated in place after an account action until the next page arrives. */
  protected readonly rows = linkedSignal(() => this.page().content);
  /** Rows whose account action is in flight; their buttons stay disabled. */
  protected readonly pendingKeys = signal<ReadonlySet<string>>(new Set());
  protected readonly total = computed(() => this.page().totalElements);

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

  /** The logged-in user cannot deactivate the own account. */
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

  protected initials(person: PersonSummary): string {
    return `${person.firstName.charAt(0)}${person.lastName.charAt(0)}`.toUpperCase();
  }

  /** The email split before the at sign, so a long one wraps there and not letter by letter. */
  protected emailParts(email: string): [string, string] {
    const at = email.indexOf('@');
    return at > 0 ? [email.slice(0, at), email.slice(at)] : [email, ''];
  }

  keyOf(person: PersonSummary): string {
    return `${person.type}:${person.id}`;
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
        this.rows.update((rows) =>
          rows.map((row) =>
            this.keyOf(row) === key
              ? {
                  ...row,
                  accountStatus: updated.status,
                  emailChangePending: updated.emailChangePending,
                  lastLoginAt: updated.lastLoginAt,
                }
              : row,
          ),
        );
        this.accountChanged.emit();
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
}
