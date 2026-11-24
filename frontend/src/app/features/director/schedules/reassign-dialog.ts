import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
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
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatTimepickerModule } from '@angular/material/timepicker';
import { map, Observable } from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { fromHourMinute, toHourMinute } from '../../../shared/forms/ec-date-adapter';
import { WorkDay } from '../../../shared/schedules/schedule-api';
import {
  DAY_LABELS,
  formatRange,
  minutesOf,
  WORK_DAYS,
} from '../../../shared/schedules/schedule-rules';
import { AssignmentsApi, AvailablePhysiotherapist, OperationsTreatment } from './assignments-api';

export interface ReassignDialogData {
  treatment: OperationsTreatment;
  /** HH:mm, the bounds of the time pickers. */
  openingTime: string;
  closingTime: string;
}

/**
 * "Reasignar fisioterapeuta" (MEJ-04): the operations director picks a day and a range of hours, and the dialog
 * offers only the physiotherapists available then (an active account and one slot that covers the range), from the
 * smallest load to the largest. The current responsible appears but cannot be chosen again. The dialog closes with
 * the treatment and its new responsible.
 */
@Component({
  selector: 'app-reassign-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTimepickerModule,
    MatRadioModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './reassign-dialog.html',
  styleUrl: './reassign-dialog.scss',
})
export class ReassignDialog {
  private readonly api = inject(AssignmentsApi);
  private readonly dialogRef =
    inject<MatDialogRef<ReassignDialog, OperationsTreatment>>(MatDialogRef);

  protected readonly data = inject<ReassignDialogData>(MAT_DIALOG_DATA);
  protected readonly treatment = this.data.treatment;
  protected readonly days = WORK_DAYS;
  protected readonly dayLabels = DAY_LABELS;
  protected readonly formatRange = formatRange;
  protected readonly minTime = fromHourMinute(this.data.openingTime);
  protected readonly maxTime = fromHourMinute(this.data.closingTime);

  protected readonly form = new FormGroup({
    day: new FormControl<WorkDay>(this.treatment.scheduleDays[0] ?? 'MONDAY', {
      nonNullable: true,
    }),
    from: new FormControl<Date | null>(fromHourMinute('08:00'), Validators.required),
    to: new FormControl<Date | null>(fromHourMinute('10:00'), Validators.required),
    physiotherapistId: new FormControl<number | null>(null, Validators.required),
  });

  private readonly range = toSignal(this.form.valueChanges.pipe(map(() => this.rangeValue())), {
    initialValue: this.rangeValue(),
  });
  /** The range problem, shown before searching. */
  protected readonly rangeProblem = computed(() => {
    const range = this.range();
    if (!range.from || !range.to) {
      return null;
    }
    return minutesOf(range.from) < minutesOf(range.to)
      ? null
      : 'La hora inicial debe ser anterior a la hora final.';
  });

  protected readonly searching = signal(false);
  /** null until the first search ends. */
  protected readonly available = signal<AvailablePhysiotherapist[] | null>(null);
  /** The day of the results, for their labels. */
  protected readonly searchedDay = signal<WorkDay>('MONDAY');
  protected readonly searchError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  constructor() {
    this.search();
  }

  protected search(): void {
    const { day, from, to } = this.rangeValue();
    if (!from || !to || this.rangeProblem()) {
      this.form.controls.from.markAsTouched();
      this.form.controls.to.markAsTouched();
      return;
    }
    this.searching.set(true);
    this.searchError.set(null);
    this.form.controls.physiotherapistId.reset(null);
    this.api.availability(day, from, to).subscribe({
      next: (available) => {
        this.searchedDay.set(day);
        this.available.set(available);
        this.searching.set(false);
      },
      error: (error: unknown) => {
        this.available.set(null);
        this.searching.set(false);
        this.searchError.set(
          problemDetailMessage(
            error,
            'No se pudo consultar la disponibilidad. Intente nuevamente.',
          ),
        );
      },
    });
  }

  protected isCurrent(physiotherapist: AvailablePhysiotherapist): boolean {
    return physiotherapist.physiotherapistId === this.treatment.responsible?.id;
  }

  protected loadLabel(physiotherapist: AvailablePhysiotherapist): string {
    const count = physiotherapist.activeTreatments;
    return count === 1 ? '1 tratamiento activo' : `${count} tratamientos activos`;
  }

  protected submit(): void {
    const chosen = this.form.controls.physiotherapistId.value;
    if (chosen === null) {
      this.form.controls.physiotherapistId.markAsTouched();
      this.errorMessage.set('Elija un fisioterapeuta disponible.');
      return;
    }
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.api.reassign(this.treatment.id, chosen).subscribe({
      next: (updated) => this.dialogRef.close(updated),
      error: (error: unknown) => {
        this.submitting.set(false);
        this.errorMessage.set(
          problemDetailMessage(error, 'No se pudo reasignar el tratamiento. Intente nuevamente.'),
        );
      },
    });
  }

  private rangeValue(): { day: WorkDay; from: string; to: string } {
    return {
      day: this.form.controls.day.value,
      from: toHourMinute(this.form.controls.from.value),
      to: toHourMinute(this.form.controls.to.value),
    };
  }
}

/** Opens the dialog; emits the treatment with its new responsible, or undefined if it was cancelled. */
export function openReassignDialog(
  dialog: MatDialog,
  data: ReassignDialogData,
): Observable<OperationsTreatment | undefined> {
  return dialog
    .open<ReassignDialog, ReassignDialogData, OperationsTreatment>(ReassignDialog, {
      data,
      width: '600px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: 'first-tabbable',
      disableClose: true,
    })
    .afterClosed();
}
