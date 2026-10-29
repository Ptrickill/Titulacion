import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Role } from '../../../core/auth/role';
import { DataTable, DataTableColumn } from '../../../shared/ui/data-table/data-table';
import { DataTableCell } from '../../../shared/ui/data-table/data-table-cell';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatCard } from '../../../shared/ui/stat-card';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { openCreateUserDialog } from './create-user-dialog';
import { formatLastAccess } from './last-access';
import { UserAccount, UserApi } from './user-api';
import { USER_ROLE_FILTERS, USER_ROLE_LABELS, USER_ROLE_TONES } from './user-roles';

type RoleFilter = Role | 'ALL';

/** Lower case without accents, so "jose" finds "José". */
function normalize(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

/**
 * "Gestión de Usuarios y Roles" (HU-02, prototype 09): account indicators, search, role filter and the
 * account table; new accounts are created in {@link CreateUserDialog}.
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
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly roleFilters = USER_ROLE_FILTERS;
  protected readonly roleLabels = USER_ROLE_LABELS;
  protected readonly roleTones = USER_ROLE_TONES;
  protected readonly formatLastAccess = formatLastAccess;

  protected readonly columns: DataTableColumn<UserAccount>[] = [
    { key: 'fullName', header: 'Usuario' },
    { key: 'role', header: 'Rol asignado' },
    { key: 'lastLoginAt', header: 'Último acceso' },
    { key: 'active', header: 'Estado' },
  ];

  protected readonly users = signal<UserAccount[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly query = signal('');
  protected readonly roleFilter = signal<RoleFilter>('ALL');

  protected readonly stats = computed(() => {
    const users = this.users();
    const count = (role: Role) => users.filter((user) => user.role === role).length;
    return {
      active: users.filter((user) => user.active).length,
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
        this.snackBar.open(`Usuario ${created.fullName} creado correctamente.`, 'Cerrar', {
          duration: 5000,
        });
        this.load();
      }
    });
  }

  protected onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }
}
