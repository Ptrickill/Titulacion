import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ScheduleSlot, WorkDay } from '../../../shared/schedules/schedule-api';
import {
  DAY_LABELS,
  DAY_SHORT_LABELS,
  formatRange,
  minutesOf,
  slotsByDay,
  WORK_DAYS,
} from '../../../shared/schedules/schedule-rules';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { BoardPhysiotherapist, ScheduleBoard as Board } from './assignments-api';

interface BoardRow {
  physiotherapist: BoardPhysiotherapist;
  byDay: Record<WorkDay, ScheduleSlot[]>;
}

interface DayEntry {
  physiotherapist: BoardPhysiotherapist;
  slots: ScheduleSlot[];
}

/**
 * Weekly schedules board (MEJ-04): one row per physiotherapist, Monday to Saturday, with its slots as blocks on the
 * clinic hours, its load and "Editar horario". On narrow screens the same data becomes a list per day, so the page
 * never scrolls sideways.
 */
@Component({
  selector: 'app-schedule-board',
  imports: [MatButtonModule, MatIconModule, StatusBadge],
  templateUrl: './schedule-board.html',
  styleUrl: './schedule-board.scss',
})
export class ScheduleBoard {
  readonly board = input.required<Board>();
  readonly edit = output<BoardPhysiotherapist>();

  protected readonly days = WORK_DAYS;
  protected readonly dayLabels = DAY_LABELS;
  protected readonly dayShortLabels = DAY_SHORT_LABELS;
  protected readonly formatRange = formatRange;

  protected readonly rows = computed<BoardRow[]>(() =>
    this.board().physiotherapists.map((physiotherapist) => ({
      physiotherapist,
      byDay: slotsByDay(physiotherapist.slots),
    })),
  );

  /** For the list per day: who works that day and when. */
  protected readonly byDay = computed(() => {
    const entries = Object.fromEntries(WORK_DAYS.map((day) => [day, [] as DayEntry[]])) as Record<
      WorkDay,
      DayEntry[]
    >;
    for (const row of this.rows()) {
      for (const day of WORK_DAYS) {
        if (row.byDay[day].length > 0) {
          entries[day].push({ physiotherapist: row.physiotherapist, slots: row.byDay[day] });
        }
      }
    }
    return entries;
  });

  /** Position of a slot on the clinic hours, as CSS percentages. */
  protected blockStyle(slot: ScheduleSlot): Record<string, string> {
    const opening = minutesOf(this.board().openingTime);
    const length = minutesOf(this.board().closingTime) - opening;
    const left = ((minutesOf(slot.start) - opening) / length) * 100;
    const width = ((minutesOf(slot.end) - minutesOf(slot.start)) / length) * 100;
    return { left: `${left}%`, width: `${width}%` };
  }

  protected loadLabel(physiotherapist: BoardPhysiotherapist): string {
    const count = physiotherapist.activeTreatments;
    return count === 1 ? '1 tratamiento activo' : `${count} tratamientos activos`;
  }
}
