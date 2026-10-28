import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { openConfirmDialog } from '../../shared/ui/confirm-dialog';
import { DataTable, DataTableColumn } from '../../shared/ui/data-table/data-table';
import { DataTableCell } from '../../shared/ui/data-table/data-table-cell';
import { EmptyState } from '../../shared/ui/empty-state';
import { FormSection } from '../../shared/ui/form-section';
import { PageHeader } from '../../shared/ui/page-header';
import { StatCard } from '../../shared/ui/stat-card';
import { BadgeTone, StatusBadge } from '../../shared/ui/status-badge';

interface SampleUser {
  name: string;
  email: string;
  role: string;
  roleTone: BadgeTone;
  lastAccess: string;
  status: string;
}

const ROLES: readonly [string, BadgeTone][] = [
  ['Fisioterapeuta', 'teal'],
  ['Administrador', 'purple'],
  ['Dirección Operativa', 'warning'],
  ['Paciente (Portal)', 'info'],
];

/** Synthetic rows: names and e-mails are invented for the component gallery. */
const SAMPLE_USERS: SampleUser[] = Array.from({ length: 13 }, (_, i) => {
  const [role, roleTone] = ROLES[i % ROLES.length];
  return {
    name: `Usuario de ejemplo ${i + 1}`,
    email: `usuario${i + 1}@ejemplo.test`,
    role,
    roleTone,
    lastAccess: i % 3 === 0 ? 'Hoy' : i === 1 ? 'Ayer' : `Hace ${i} días`,
    status: i % 5 === 4 ? 'Inactivo' : 'Activo',
  };
});

/**
 * Development-only gallery of the shared UI components (route /ui-kit, hidden in production).
 * Used to review the design system against the prototype without real data.
 */
@Component({
  selector: 'app-ui-kit',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    PageHeader,
    StatCard,
    StatusBadge,
    DataTable,
    DataTableCell,
    FormSection,
    EmptyState,
  ],
  templateUrl: './ui-kit.html',
  styleUrl: './ui-kit.scss',
})
export class UiKit {
  private readonly dialog = inject(MatDialog);

  protected readonly users = SAMPLE_USERS;
  protected readonly columns: DataTableColumn<SampleUser>[] = [
    { key: 'name', header: 'Usuario' },
    { key: 'role', header: 'Rol asignado' },
    { key: 'lastAccess', header: 'Último acceso' },
    { key: 'status', header: 'Estado', align: 'center' },
  ];
  protected readonly lastDialogResult = signal<string | null>(null);

  protected openDialog(tone: 'primary' | 'danger'): void {
    openConfirmDialog(this.dialog, {
      title: tone === 'danger' ? 'Marcar tratamiento como abandonado' : 'Guardar cambios',
      message:
        tone === 'danger'
          ? 'El tratamiento dejará de estar activo. Esta acción quedará registrada en la auditoría.'
          : '¿Desea guardar los cambios realizados?',
      confirmLabel: tone === 'danger' ? 'Marcar como abandonado' : 'Guardar',
      tone,
    }).subscribe((confirmed) => this.lastDialogResult.set(confirmed ? 'Confirmado' : 'Cancelado'));
  }
}
