import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ScheduleApi, StaffSchedule } from './schedule-api';
import { openScheduleEditorDialog } from './schedule-editor-dialog';
import { WeeklySchedule } from './weekly-schedule';

/**
 * "Horario" of a physiotherapist's record (MEJ-04): the weekly schedule and "Editar horario". The administrator uses
 * it in the staff record; the operations director edits from the schedules board with the same dialog.
 */
@Component({
  selector: 'app-staff-schedule-panel',
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule, WeeklySchedule],
  template: `
    @if (schedule.isLoading() && !saved()) {
      <mat-progress-bar mode="indeterminate" aria-label="Cargando horario" />
    } @else if (schedule.error() && !saved()) {
      <p class="error" role="alert">
        <mat-icon aria-hidden="true">error</mat-icon>
        <span>No se pudo cargar el horario.</span>
        <button mat-button type="button" (click)="schedule.reload()">Reintentar</button>
      </p>
    } @else if (current(); as week) {
      <div class="heading">
        <p class="hours">Jornada de la clínica: {{ week.openingTime }} a {{ week.closingTime }}</p>
        <button mat-stroked-button type="button" class="edit-schedule" (click)="edit(week)">
          <mat-icon aria-hidden="true">edit_calendar</mat-icon>
          Editar horario
        </button>
      </div>
      <app-weekly-schedule [slots]="week.slots" />
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .heading {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 16px;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
    }

    .hours {
      margin: 0;
      color: var(--re-text-muted);
      font-size: 13px;
    }

    .error {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      color: var(--re-danger);
      font-size: 13px;
    }
  `,
})
export class StaffSchedulePanel {
  private readonly api = inject(ScheduleApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly staffId = input.required<number>();

  protected readonly schedule = rxResource({
    params: () => this.staffId(),
    stream: ({ params }) => this.api.ofStaff(params),
  });
  /** The schedule saved in this page, which replaces the loaded one. */
  protected readonly saved = signal<StaffSchedule | null>(null);

  /** Read only after the loading and error branches: a resource in error throws when its value is read. */
  protected readonly current = computed(() => this.saved() ?? this.schedule.value());

  protected edit(week: StaffSchedule): void {
    openScheduleEditorDialog(this.dialog, week).subscribe((result) => {
      if (result) {
        this.saved.set(result);
        this.snackBar.open('Horario guardado correctamente.', 'Cerrar', { duration: 5000 });
      }
    });
  }
}
