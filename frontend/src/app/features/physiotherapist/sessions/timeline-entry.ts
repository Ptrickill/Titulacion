import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { DataTable, DataTableColumn } from '../../../shared/ui/data-table/data-table';
import { DataTableCell } from '../../../shared/ui/data-table/data-table-cell';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { formatAge } from '../../admin/patients/patient-age';
import {
  patientInitials,
  TREATMENT_STATUS_LABELS,
  TREATMENT_STATUS_TONES,
} from '../clinical-records/clinical-labels';
import { PhysioPatient } from '../clinical-records/clinical-record-api';
import { PhysioPatientSearch } from '../patients/physio-patient-search';

const PAGE_SIZE = 10;

/**
 * Entry of "Línea de Tiempo" in the menu (HU-11): the timeline belongs to a treatment, so this page lets the
 * physiotherapist choose the patient. It lists the same patients as "Gestión de Pacientes", with the same search and
 * pagination; a patient with a treatment opens the timeline of the current one (the active one, otherwise the most
 * recent), and a patient without treatment only shows "Sin tratamiento".
 */
@Component({
  selector: 'app-timeline-entry',
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
  templateUrl: './timeline-entry.html',
  styleUrl: './timeline-entry.scss',
})
export class TimelineEntry {
  protected readonly patients = new PhysioPatientSearch(PAGE_SIZE);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly formatAge = formatAge;
  protected readonly initials = patientInitials;
  protected readonly statusLabels = TREATMENT_STATUS_LABELS;
  protected readonly statusTones = TREATMENT_STATUS_TONES;

  /** Every column has its own cell template; the keys only name them. */
  protected readonly columns: DataTableColumn<PhysioPatient>[] = [
    { key: 'lastName', header: 'Paciente' },
    { key: 'currentTreatment', header: 'Diagnóstico' },
    { key: 'age', header: 'Tratamiento' },
    { key: 'id', header: 'Acción', align: 'end' },
  ];

  protected plannedLabel(plannedSessions: number): string {
    return plannedSessions === 1
      ? '1 sesión planificada'
      : `${plannedSessions} sesiones planificadas`;
  }
}
