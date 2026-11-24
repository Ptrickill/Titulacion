import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
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
import { MatTimepickerModule } from '@angular/material/timepicker';
import { map, Observable } from 'rxjs';
import { problemDetailMessage } from '../../core/http/problem-detail';
import { fromHourMinute, toHourMinute } from '../forms/ec-date-adapter';
import { ScheduleApi, ScheduleSlot, StaffSchedule, WorkDay } from './schedule-api';
import { DAY_LABELS, minutesOf, slotProblems, WORK_DAYS } from './schedule-rules';

export interface ScheduleEditorDialogData {
  schedule: StaffSchedule;
}

type SlotForm = FormGroup<{
  day: FormControl<WorkDay | null>;
  start: FormControl<Date | null>;
  end: FormControl<Date | null>;
}>;

/**
 * "Editar horario" (MEJ-04): the administrator or the operations director edits the weekly slots of a
 * physiotherapist with time pickers. Each row shows its problem as soon as it has one (outside the clinic hours,
 * inverted or overlapping another slot of the day), with the API's rules; the API repeats them and merges the
 * contiguous slots. The dialog closes with the saved schedule.
 */
@Component({
  selector: 'app-schedule-editor-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTimepickerModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './schedule-editor-dialog.html',
  styleUrl: './schedule-editor-dialog.scss',
})
export class ScheduleEditorDialog {
  private readonly api = inject(ScheduleApi);
  private readonly dialogRef =
    inject<MatDialogRef<ScheduleEditorDialog, StaffSchedule>>(MatDialogRef);

  protected readonly schedule = inject<ScheduleEditorDialogData>(MAT_DIALOG_DATA).schedule;
  protected readonly days = WORK_DAYS;
  protected readonly dayLabels = DAY_LABELS;
  protected readonly minTime = fromHourMinute(this.schedule.openingTime);
  protected readonly maxTime = fromHourMinute(this.schedule.closingTime);

  protected readonly form = new FormGroup({
    slots: new FormArray<SlotForm>(this.schedule.slots.map((slot) => this.slotForm(slot))),
  });

  private readonly value = toSignal(this.form.valueChanges.pipe(map(() => this.currentSlots())), {
    initialValue: this.currentSlots(),
  });
  /** The problem of each row, or null. */
  protected readonly problems = computed(() =>
    slotProblems(this.value(), this.schedule.openingTime, this.schedule.closingTime),
  );
  protected readonly hasProblems = computed(() => this.problems().some((problem) => !!problem));

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected get slots(): FormArray<SlotForm> {
    return this.form.controls.slots;
  }

  /** A new row on the first day without slots, in the morning (within the clinic hours). */
  protected addSlot(): void {
    const used = new Set(this.slots.controls.map((slot) => slot.controls.day.value));
    const day = WORK_DAYS.find((candidate) => !used.has(candidate)) ?? 'MONDAY';
    const opening = minutesOf(this.schedule.openingTime);
    const closing = minutesOf(this.schedule.closingTime);
    const start = Math.max(opening, 8 * 60);
    const end = Math.min(closing, start + 4 * 60);
    this.slots.push(this.slotForm({ day, start: timeOf(start), end: timeOf(end) }));
  }

  protected removeSlot(index: number): void {
    this.slots.removeAt(index);
  }

  protected save(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid || this.hasProblems()) {
      this.form.markAllAsTouched();
      this.errorMessage.set('Revise las franjas marcadas antes de guardar.');
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.api.replace(this.schedule.staffId, this.currentSlots() as ScheduleSlot[]).subscribe({
      next: (saved) => this.dialogRef.close(saved),
      error: (error: unknown) => {
        this.submitting.set(false);
        this.errorMessage.set(
          problemDetailMessage(error, 'No se pudo guardar el horario. Intente nuevamente.'),
        );
      },
    });
  }

  private currentSlots(): Partial<ScheduleSlot>[] {
    return this.form.controls.slots.controls.map((slot) => ({
      day: slot.controls.day.value ?? undefined,
      start: toHourMinute(slot.controls.start.value) || undefined,
      end: toHourMinute(slot.controls.end.value) || undefined,
    }));
  }

  private slotForm(slot: ScheduleSlot): SlotForm {
    return new FormGroup({
      day: new FormControl<WorkDay | null>(slot.day, Validators.required),
      start: new FormControl<Date | null>(fromHourMinute(slot.start), Validators.required),
      end: new FormControl<Date | null>(fromHourMinute(slot.end), Validators.required),
    });
  }
}

function timeOf(minutes: number): string {
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
}

/** Opens the editor; emits the saved schedule, or undefined if it was cancelled. */
export function openScheduleEditorDialog(
  dialog: MatDialog,
  schedule: StaffSchedule,
): Observable<StaffSchedule | undefined> {
  return dialog
    .open<ScheduleEditorDialog, ScheduleEditorDialogData, StaffSchedule>(ScheduleEditorDialog, {
      data: { schedule },
      width: '680px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: 'first-tabbable',
      disableClose: true,
    })
    .afterClosed();
}
