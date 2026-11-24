import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { Page } from '../../../core/http/page';
import { WorkDay } from '../../../shared/schedules/schedule-api';
import { DAY_LABELS, DAY_SHORT_LABELS } from '../../../shared/schedules/schedule-rules';
import { DataTable, DataTableColumn } from '../../../shared/ui/data-table/data-table';
import { DataTableCell } from '../../../shared/ui/data-table/data-table-cell';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { OperationsTreatment } from './assignments-api';

/**
 * Active treatments of the operations director (MEJ-04): the patient's name, the current responsible ("Responsable
 * inactivo" when its account is deactivated), the start date and the days of the responsible's schedule, with
 * "Reasignar". Never a diagnosis, a plan, notes nor sessions. The filter keeps only the treatments whose responsible
 * is inactive.
 */
@Component({
  selector: 'app-assignment-list',
  imports: [
    DatePipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    DataTable,
    DataTableCell,
    StatusBadge,
  ],
  templateUrl: './assignment-list.html',
  styleUrl: './assignment-list.scss',
})
export class AssignmentList {
  readonly page = input.required<Page<OperationsTreatment>>();
  readonly pageSize = input.required<number>();
  readonly onlyInactiveResponsible = input.required<boolean>();
  readonly pageChange = output<number>();
  readonly filterChange = output<boolean>();
  readonly reassign = output<OperationsTreatment>();

  protected readonly dayLabels = DAY_LABELS;
  protected readonly dayShortLabels = DAY_SHORT_LABELS;
  protected readonly columns: DataTableColumn<OperationsTreatment>[] = [
    { key: 'patientName', header: 'Paciente' },
    { key: 'responsible', header: 'Responsable vigente' },
    { key: 'scheduleDays', header: 'Días del horario', wrapHeader: true },
    { key: 'id', header: 'Acciones', align: 'end' },
  ];

  protected daysLabel(days: readonly WorkDay[]): string {
    return days.map((day) => this.dayLabels[day]).join(', ');
  }
}
