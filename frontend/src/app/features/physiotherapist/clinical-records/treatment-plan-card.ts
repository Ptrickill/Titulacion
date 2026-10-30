import { DatePipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { openChangeResponsibleDialog } from './change-responsible-dialog';
import { openChangeStatusDialog } from './change-status-dialog';
import { initialsOf, TREATMENT_STATUS_LABELS, TREATMENT_STATUS_TONES } from './clinical-labels';
import { Treatment } from './clinical-record-api';
import { TreatmentApi, TreatmentHistory } from './treatment-api';

/**
 * "Plan terapéutico y asignación clínica" (HU-08, prototype 04): therapy plan, status and current responsible of a
 * treatment, the actions "Cambiar responsable" and "Cambiar estado", and the history of both. The full clinical
 * record view (HU-09) shows a read-only version and links here.
 */
@Component({
  selector: 'app-treatment-plan-card',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressBarModule, StatusBadge],
  host: { role: 'region', 'aria-labelledby': 'treatment-plan-title' },
  templateUrl: './treatment-plan-card.html',
  styleUrl: './treatment-plan-card.scss',
})
export class TreatmentPlanCard {
  private readonly api = inject(TreatmentApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly treatment = input.required<Treatment>();

  protected readonly statusLabels = TREATMENT_STATUS_LABELS;
  protected readonly statusTones = TREATMENT_STATUS_TONES;

  private readonly historyResource = rxResource({
    params: () => this.treatment().id,
    stream: ({ params: treatmentId }) => this.api.history(treatmentId),
  });
  protected readonly history = computed(() =>
    this.historyResource.hasValue() ? this.historyResource.value() : undefined,
  );
  protected readonly historyError = computed(() => this.historyResource.error() !== undefined);

  /** The history is newer than the treatment input after a change made here. */
  protected readonly status = computed(() => this.history()?.status ?? this.treatment().status);
  protected readonly canChangeResponsible = computed(
    () => this.history() !== undefined && this.status() === 'ACTIVE',
  );
  protected readonly canChangeStatus = computed(
    () => (this.history()?.allowedNextStatuses.length ?? 0) > 0,
  );

  protected readonly initials = initialsOf;

  protected reload(): void {
    this.historyResource.reload();
  }

  protected changeResponsible(): void {
    const current = this.history()?.currentResponsible ?? null;
    this.applyChange(
      openChangeResponsibleDialog(this.dialog, {
        treatmentId: this.treatment().id,
        currentResponsibleId: current?.physiotherapistId ?? null,
        currentResponsibleName: current?.fullName ?? null,
      }),
      'Responsable del tratamiento actualizado.',
    );
  }

  protected changeStatus(): void {
    this.applyChange(
      openChangeStatusDialog(this.dialog, {
        treatmentId: this.treatment().id,
        currentStatus: this.status(),
        allowedStatuses: this.history()?.allowedNextStatuses ?? [],
      }),
      'Estado del tratamiento actualizado.',
    );
  }

  private applyChange(
    dialogResult: Observable<TreatmentHistory | undefined>,
    message: string,
  ): void {
    dialogResult.subscribe((history) => {
      if (history) {
        this.historyResource.set(history);
        this.snackBar.open(message, 'Cerrar', { duration: 5000 });
      }
    });
  }
}
