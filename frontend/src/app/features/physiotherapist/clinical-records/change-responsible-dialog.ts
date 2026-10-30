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
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Observable } from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { Physiotherapist, TreatmentApi, TreatmentHistory } from './treatment-api';

export interface ChangeResponsibleDialogData {
  treatmentId: number;
  currentResponsibleId: number | null;
  currentResponsibleName: string | null;
}

/**
 * "Cambiar responsable" (HU-08): chooses another active physiotherapist. The server ends the current assignment and
 * starts the new one; the dialog closes with the updated history.
 */
@Component({
  selector: 'app-change-responsible-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './change-responsible-dialog.html',
  styleUrl: './change-responsible-dialog.scss',
})
export class ChangeResponsibleDialog {
  private readonly api = inject(TreatmentApi);
  private readonly dialogRef =
    inject<MatDialogRef<ChangeResponsibleDialog, TreatmentHistory>>(MatDialogRef);

  protected readonly data = inject<ChangeResponsibleDialogData>(MAT_DIALOG_DATA);

  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly form = this.fb.group({
    physiotherapistId: this.fb.control<number | null>(null, Validators.required),
  });

  /** Active physiotherapists except the current responsible. */
  protected readonly candidates = signal<Physiotherapist[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  constructor() {
    this.api.listPhysiotherapists().subscribe({
      next: (physiotherapists) => {
        this.candidates.set(
          physiotherapists.filter(
            (physiotherapist) => physiotherapist.id !== this.data.currentResponsibleId,
          ),
        );
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.api
      .changeResponsible(this.data.treatmentId, this.form.controls.physiotherapistId.value!)
      .subscribe({
        next: (history) => this.dialogRef.close(history),
        error: (error: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(
            problemDetailMessage(error, 'No se pudo cambiar el responsable. Intente nuevamente.'),
          );
        },
      });
  }
}

/** Opens the dialog; emits the updated history, or undefined if it was cancelled. */
export function openChangeResponsibleDialog(
  dialog: MatDialog,
  data: ChangeResponsibleDialogData,
): Observable<TreatmentHistory | undefined> {
  return dialog
    .open<ChangeResponsibleDialog, ChangeResponsibleDialogData, TreatmentHistory>(
      ChangeResponsibleDialog,
      {
        data,
        width: '520px',
        maxWidth: 'calc(100vw - 32px)',
        autoFocus: 'first-tabbable',
        disableClose: true,
      },
    )
    .afterClosed();
}
