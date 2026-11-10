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
  SEVERITY_LABELS,
  SEVERITY_TONES,
  TREATMENT_STATUS_LABELS,
  TREATMENT_STATUS_TONES,
} from '../clinical-records/clinical-labels';
import { PhysioPatient } from '../clinical-records/clinical-record-api';
import { PhysioPatientSearch } from './physio-patient-search';

const PAGE_SIZE = 10;

/**
 * "Gestión de Pacientes" of the physiotherapist (HU-07, prototype 03): patients with the summary of their current
 * treatment, searched by name or national ID and paginated in the server. Patients without a clinical record offer
 * "Crear historial"; records without an active treatment offer "Nuevo tratamiento". Every patient with a treatment
 * links to its plan and to its timeline (HU-11).
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
  protected readonly patients = new PhysioPatientSearch(PAGE_SIZE);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly formatAge = formatAge;
  protected readonly initials = patientInitials;
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
}
