import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Page } from '../../../core/http/page';
import {
  TREATMENT_STATUS_LABELS,
  TREATMENT_STATUS_TONES,
} from '../../../shared/clinical/treatment-labels';
import { DataTable, DataTableColumn } from '../../../shared/ui/data-table/data-table';
import { DataTableCell } from '../../../shared/ui/data-table/data-table-cell';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { ReportTreatment } from './report-api';

/**
 * "Tratamientos (N)" (HU-14, HU-15, prototype 13): one page of the treatments of the filters, with the patient, the ICD-10
 * diagnosis, the current responsible, the status and the start date. Only the name of the patient: no national ID
 * nor other personal data. Read only: the only controls are those of the pager.
 */
@Component({
  selector: 'app-report-treatment-list',
  imports: [DatePipe, MatIconModule, DataTable, DataTableCell, StatusBadge],
  templateUrl: './report-treatment-list.html',
  styleUrl: './report-treatment-list.scss',
})
export class ReportTreatmentList {
  readonly page = input.required<Page<ReportTreatment>>();
  readonly pageSize = input.required<number>();
  /** Label of the status filter, e.g. "Todos" or "Activo". */
  readonly statusFilter = input.required<string>();
  /** Label of the physiotherapist filter, e.g. "Todos" or the name. */
  readonly physiotherapistFilter = input.required<string>();
  /**
   * Label of the date range (HU-15), e.g. "Del 01/10/2026 al 31/10/2026"; null without a range. With a range, the
   * list also says that the status is the current one, not the one of the range.
   */
  readonly periodFilter = input<string | null>(null);
  readonly pageChange = output<number>();

  protected readonly statusLabels = TREATMENT_STATUS_LABELS;
  protected readonly statusTones = TREATMENT_STATUS_TONES;
  protected readonly columns: DataTableColumn<ReportTreatment>[] = [
    { key: 'patientName', header: 'Paciente y diagnóstico' },
    { key: 'status', header: 'Estado', align: 'end' },
  ];
}
