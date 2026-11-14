import { DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { SESSION_STATUS_LABELS, SESSION_STATUS_TONES } from '../../shared/clinical/session-labels';
import { painScoreTone } from '../../shared/clinical/treatment-labels';
import { DataTable, DataTableColumn } from '../../shared/ui/data-table/data-table';
import { DataTableCell } from '../../shared/ui/data-table/data-table-cell';
import { StatusBadge } from '../../shared/ui/status-badge';
import { PortalSession } from './portal-api';

/**
 * "Listado de sesiones" (HU-13, prototype 14): every session of the treatment in chronological order, with its
 * physiotherapist, EVA, activities, instructions for the patient and status, and the total of sessions that took
 * place. Read only: no row has an action. The long texts wrap and the long headers may break into lines, so the
 * table fits its card without horizontal scroll.
 */
@Component({
  selector: 'app-portal-session-list',
  imports: [DatePipe, DataTable, DataTableCell, StatusBadge],
  templateUrl: './portal-session-list.html',
  styleUrl: './portal-session-list.scss',
})
export class PortalSessionList {
  /** In chronological order, as the API sends them. */
  readonly sessions = input.required<PortalSession[]>();

  protected readonly painTone = painScoreTone;
  protected readonly statusLabels = SESSION_STATUS_LABELS;
  protected readonly statusTones = SESSION_STATUS_TONES;

  protected readonly columns: DataTableColumn<PortalSession>[] = [
    { key: 'sequenceNumber', header: 'Sesión y fecha' },
    { key: 'responsibleName', header: 'Fisioterapeuta responsable', wrapHeader: true },
    { key: 'painScore', header: 'Intensidad EVA', align: 'center', wrapHeader: true },
    { key: 'activitiesPerformed', header: 'Actividades realizadas', wrapHeader: true },
    { key: 'status', header: 'Estado' },
  ];

  /** Sessions that took place; rescheduled sessions and no-shows are listed but do not count. */
  protected readonly completed = computed(
    () => this.sessions().filter((session) => session.status === 'COMPLETED').length,
  );

  protected sessionName(session: PortalSession): string {
    return `Sesión ${String(session.sequenceNumber).padStart(2, '0')}`;
  }
}
