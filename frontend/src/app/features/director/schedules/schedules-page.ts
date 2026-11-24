import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { openScheduleEditorDialog } from '../../../shared/schedules/schedule-editor-dialog';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { AssignmentList } from './assignment-list';
import {
  AssignmentsApi,
  BoardPhysiotherapist,
  OperationsTreatment,
  ScheduleBoard as Board,
} from './assignments-api';
import { openReassignDialog } from './reassign-dialog';
import { ScheduleBoard } from './schedule-board';

/** Rows of the treatment list per page. */
export const ASSIGNMENTS_PAGE_SIZE = 10;

/**
 * "Horarios y asignaciones" of the operations director (MEJ-04): the weekly board of the physiotherapists (with
 * "Editar horario") and the active treatments with "Reasignar", which offers only the physiotherapists available on
 * the chosen day and hours. No clinical data. After a change, the board reloads (the loads change) and so does the
 * list.
 */
@Component({
  selector: 'app-schedules-page',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    PageHeader,
    EmptyState,
    ScheduleBoard,
    AssignmentList,
  ],
  templateUrl: './schedules-page.html',
  styleUrl: './schedules-page.scss',
})
export class SchedulesPage {
  private readonly api = inject(AssignmentsApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly pageSize = ASSIGNMENTS_PAGE_SIZE;
  protected readonly pageIndex = signal(0);
  protected readonly onlyInactiveResponsible = signal(false);

  protected readonly board = rxResource({ stream: () => this.api.board() });
  protected readonly treatments = rxResource({
    params: () => ({ page: this.pageIndex(), inactive: this.onlyInactiveResponsible() }),
    stream: ({ params }) => this.api.treatments(params.page, this.pageSize, params.inactive),
  });

  protected changeFilter(onlyInactive: boolean): void {
    this.onlyInactiveResponsible.set(onlyInactive);
    this.pageIndex.set(0);
  }

  protected editSchedule(physiotherapist: BoardPhysiotherapist, board: Board): void {
    openScheduleEditorDialog(this.dialog, {
      staffId: physiotherapist.staffId,
      fullName: physiotherapist.fullName,
      openingTime: board.openingTime,
      closingTime: board.closingTime,
      slots: physiotherapist.slots,
    }).subscribe((saved) => {
      if (saved) {
        this.board.reload();
        this.treatments.reload();
        this.snackBar.open(`Horario de ${saved.fullName} guardado correctamente.`, 'Cerrar', {
          duration: 5000,
        });
      }
    });
  }

  protected reassign(treatment: OperationsTreatment, board: Board): void {
    openReassignDialog(this.dialog, {
      treatment,
      openingTime: board.openingTime,
      closingTime: board.closingTime,
    }).subscribe((updated) => {
      if (updated) {
        this.board.reload();
        this.treatments.reload();
        this.snackBar.open(
          `Responsable actualizado: ${updated.responsible?.fullName ?? 'sin asignar'}.`,
          'Cerrar',
          { duration: 5000 },
        );
      }
    });
  }
}
