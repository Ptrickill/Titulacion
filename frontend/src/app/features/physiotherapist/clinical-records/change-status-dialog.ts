import { TextFieldModule } from '@angular/cdk/text-field';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Observable } from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { TREATMENT_STATUS_LABELS } from './clinical-labels';
import { TreatmentStatus } from './clinical-record-api';
import { STATUS_CHANGE_REASON_MAX, TreatmentApi, TreatmentHistory } from './treatment-api';

export interface ChangeStatusDialogData {
  treatmentId: number;
  currentStatus: TreatmentStatus;
  /** Statuses allowed from the current one, as the server reports them. */
  allowedStatuses: readonly TreatmentStatus[];
}

/**
 * "Cambiar estado" (HU-08): offers only the transitions allowed from the current status (ACTIVE → COMPLETED or
 * ABANDONED; ABANDONED → ACTIVE) and requires a reason. Closes with the updated history.
 */
@Component({
  selector: 'app-change-status-dialog',
  imports: [
    ReactiveFormsModule,
    TextFieldModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './change-status-dialog.html',
  styleUrl: './change-status-dialog.scss',
})
export class ChangeStatusDialog {
  private readonly api = inject(TreatmentApi);
  private readonly dialogRef =
    inject<MatDialogRef<ChangeStatusDialog, TreatmentHistory>>(MatDialogRef);

  protected readonly data = inject<ChangeStatusDialogData>(MAT_DIALOG_DATA);
  protected readonly statusLabels = TREATMENT_STATUS_LABELS;
  protected readonly reasonMax = STATUS_CHANGE_REASON_MAX;

  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly form = this.fb.group({
    // With a single option (resuming an abandoned treatment) it is already chosen
    newStatus: this.fb.control<TreatmentStatus | null>(
      this.data.allowedStatuses.length === 1 ? this.data.allowedStatuses[0] : null,
      Validators.required,
    ),
    reason: ['', [Validators.required, Validators.maxLength(STATUS_CHANGE_REASON_MAX)]],
  });

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected submit(): void {
    const reason = this.form.controls.reason.value.trim();
    if (!reason) {
      // Only spaces is not a reason (the backend rejects it too)
      this.form.controls.reason.setValue('');
    }
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.api
      .changeStatus(this.data.treatmentId, {
        newStatus: this.form.controls.newStatus.value!,
        reason,
      })
      .subscribe({
        next: (history) => this.dialogRef.close(history),
        error: (error: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(
            problemDetailMessage(error, 'No se pudo cambiar el estado. Intente nuevamente.'),
          );
        },
      });
  }
}

/** Opens the dialog; emits the updated history, or undefined if it was cancelled. */
export function openChangeStatusDialog(
  dialog: MatDialog,
  data: ChangeStatusDialogData,
): Observable<TreatmentHistory | undefined> {
  return dialog
    .open<ChangeStatusDialog, ChangeStatusDialogData, TreatmentHistory>(ChangeStatusDialog, {
      data,
      width: '520px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: 'first-tabbable',
      disableClose: true,
    })
    .afterClosed();
}
