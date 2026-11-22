import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter, finalize, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth-service';
import { Role } from '../../../core/auth/role';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { openConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { DataTable, DataTableColumn } from '../../../shared/ui/data-table/data-table';
import { DataTableCell } from '../../../shared/ui/data-table/data-table-cell';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatCard } from '../../../shared/ui/stat-card';
import { BadgeTone, StatusBadge } from '../../../shared/ui/status-badge';
import { openCreateUserDialog } from './create-user-dialog';
import { formatLastAccess } from './last-access';
import { AccountStatus, UserAccount, UserApi } from './user-api';
import { USER_ROLE_FILTERS, USER_ROLE_LABELS, USER_ROLE_TONES } from './user-roles';

type RoleFilter = Role | 'ALL';

/** Badge of each account status (MEJ-01). */
const STATUS_BADGES: Record<AccountStatus, { label: string; tone: BadgeTone }> = {
  ACTIVE: { label: 'Activo', tone: 'success' },
  PENDING_ACTIVATION: { label: 'Pendiente de activación', tone: 'warning' },
  INACTIVE: { label: 'Inactivo', tone: 'neutral' },
};

/** Lower case without accents, so "jose" finds "José". */
function normalize(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

/**
 * "Gestión de Usuarios y Roles" (HU-02, prototype 09): account indicators, search, role filter and the
 * account table; new accounts are created in {@link CreateUserDialog} and each row can be activated or
 * deactivated (HU-03). A new account stays "Pendiente de activación" until its owner follows the emailed link, and
 * its row offers "Reenviar invitación" (MEJ-01).
 */
@Component({
  selector: 'app-user-list',
  imports: [
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatTooltipModule,
    PageHeader,
    StatCard,
    StatusBadge,
    DataTable,
    DataTableCell,
    EmptyState,
  ],
  templateUrl: './user-list.html',
  styleUrl: './user-list.scss',
})
export class UserList implements OnInit {
  private readonly api = inject(UserApi);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly roleFilters = USER_ROLE_FILTERS;
  protected readonly roleLabels = USER_ROLE_LABELS;
  protected readonly roleTones = USER_ROLE_TONES;
  protected readonly formatLastAccess = formatLastAccess;
  protected readonly statusBadges = STATUS_BADGES;

  protected readonly columns: DataTableColumn<UserAccount>[] = [
    { key: 'fullName', header: 'Usuario' },
    { key: 'role', header: 'Rol asignado' },
    { key: 'lastLoginAt', header: 'Último acceso' },
    { key: 'status', header: 'Estado' },
    { key: 'id', header: 'Acciones', align: 'end' },
  ];

  protected readonly users = signal<UserAccount[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly query = signal('');
  protected readonly roleFilter = signal<RoleFilter>('ALL');
  /** Accounts whose status change is in flight; their action button stays disabled. */
  protected readonly pendingIds = signal<ReadonlySet<number>>(new Set());

  protected readonly stats = computed(() => {
    const users = this.users();
    const count = (role: Role) => users.filter((user) => user.role === role).length;
    return {
      active: users.filter((user) => user.status === 'ACTIVE').length,
      physiotherapists: count('PHYSIOTHERAPIST'),
      portalPatients: count('PATIENT'),
      administrators: count('ADMINISTRATOR'),
    };
  });

  protected readonly filteredUsers = computed(() => {
    const query = normalize(this.query().trim());
    const role = this.roleFilter();
    return this.users().filter(
      (user) =>
        (role === 'ALL' || user.role === role) &&
        (!query || normalize(`${user.fullName} ${user.email}`).includes(query)),
    );
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.api.findAll().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  protected openCreateDialog(): void {
    openCreateUserDialog(this.dialog).subscribe((created) => {
      if (created) {
        this.snackBar.open(`Enviamos un enlace de activación a ${created.email}`, 'Cerrar', {
          duration: 6000,
        });
        this.load();
      }
    });
  }

  /** Asks for confirmation, then activates or deactivates the account and updates only its row (HU-03). */
  protected toggleStatus(user: UserAccount): void {
    // The button stays hoverable while disabled (for its tooltip), so guard here too
    if (this.isOwnAccount(user) || this.pendingIds().has(user.id)) {
      return;
    }
    const deactivate = user.active;
    openConfirmDialog(this.dialog, {
      title: deactivate ? 'Desactivar cuenta' : 'Activar cuenta',
      message: deactivate
        ? `¿Desactivar la cuenta de ${user.fullName}? No podrá iniciar sesión hasta que la reactive.`
        : `¿Activar la cuenta de ${user.fullName}? Podrá volver a iniciar sesión.`,
      confirmLabel: deactivate ? 'Desactivar' : 'Activar',
      tone: deactivate ? 'danger' : 'primary',
      icon: deactivate ? 'lock' : 'lock_open',
    })
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.setPending(user.id, true);
          return this.api
            .changeStatus(user.id, !deactivate)
            .pipe(finalize(() => this.setPending(user.id, false)));
        }),
      )
      .subscribe({
        next: (updated) => {
          this.users.update((users) => users.map((u) => (u.id === updated.id ? updated : u)));
          this.snackBar.open(
            updated.active ? 'Cuenta activada correctamente.' : 'Cuenta desactivada correctamente.',
            'Cerrar',
            { duration: 5000 },
          );
        },
        error: (error: unknown) => {
          this.snackBar.open(
            problemDetailMessage(
              error,
              'No se pudo cambiar el estado de la cuenta. Intente nuevamente.',
            ),
            'Cerrar',
            { duration: 6000 },
          );
        },
      });
  }

  /** Sends a new activation link to a pending account (MEJ-01); the previous links stop working. */
  protected resendInvitation(user: UserAccount): void {
    if (this.pendingIds().has(user.id)) {
      return;
    }
    this.setPending(user.id, true);
    this.api
      .resendInvitation(user.id)
      .pipe(finalize(() => this.setPending(user.id, false)))
      .subscribe({
        next: (updated) => {
          this.users.update((users) => users.map((u) => (u.id === updated.id ? updated : u)));
          this.snackBar.open(
            `Enviamos un nuevo enlace de activación a ${updated.email}`,
            'Cerrar',
            {
              duration: 6000,
            },
          );
        },
        error: (error: unknown) => {
          this.snackBar.open(
            problemDetailMessage(error, 'No se pudo reenviar la invitación. Intente nuevamente.'),
            'Cerrar',
            { duration: 6000 },
          );
        },
      });
  }

  protected isOwnAccount(user: UserAccount): boolean {
    return user.id === this.auth.user()?.id;
  }

  private setPending(id: number, pending: boolean): void {
    this.pendingIds.update((ids) => {
      const next = new Set(ids);
      if (pending) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  protected onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }
}
