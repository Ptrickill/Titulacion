import { DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { ScheduleSlot } from './schedule-api';
import { DAY_LABELS, formatRange, slotsByDay, weeklyHours, WORK_DAYS } from './schedule-rules';

/**
 * A weekly schedule, read only (MEJ-04): one row per day from Monday to Saturday with its slots, "Sin atención" on a
 * day without slots, and the weekly total. Used by the staff record and by "Mi horario".
 */
@Component({
  selector: 'app-weekly-schedule',
  imports: [DecimalPipe],
  template: `
    <dl class="week">
      @for (day of days; track day) {
        <div class="day" [class.off]="byDay()[day].length === 0">
          <dt>{{ dayLabels[day] }}</dt>
          <dd>
            @for (slot of byDay()[day]; track slot.start) {
              <span class="slot">{{ formatRange(slot) }}</span>
            } @empty {
              <span class="none">Sin atención</span>
            }
          </dd>
        </div>
      }
    </dl>
    <p class="total">
      @if (slots().length > 0) {
        Total semanal: <strong>{{ hours() | number: '1.0-1' }} h</strong>
      } @else {
        Aún no tiene franjas registradas.
      }
    </p>
  `,
  styles: `
    :host {
      display: block;
    }

    .week {
      display: grid;
      gap: 6px;
      margin: 0;
    }

    .day {
      display: grid;
      grid-template-columns: 96px minmax(0, 1fr);
      gap: 12px;
      align-items: center;
      padding: 8px 12px;
      background: var(--re-gray-50);
      border: 1px solid var(--re-gray-200);
      border-radius: var(--re-radius-sm);

      &.off {
        background: transparent;
        border-style: dashed;
      }
    }

    dt {
      color: var(--re-text);
      font-size: 13px;
      font-weight: 700;
    }

    dd {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin: 0;
    }

    .slot {
      padding: 3px 10px;
      color: var(--re-text-teal);
      font-size: 13px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
      background: var(--re-teal-50);
      border: 1px solid var(--re-teal-100);
      border-radius: var(--re-radius-pill);
    }

    .none {
      color: var(--re-text-muted);
      font-size: 13px;
    }

    .total {
      margin: 12px 0 0;
      color: var(--re-text-muted);
      font-size: 13px;

      strong {
        color: var(--re-text);
      }
    }

    @media (max-width: 479.98px) {
      .day {
        grid-template-columns: 1fr;
        gap: 6px;
      }
    }
  `,
})
export class WeeklySchedule {
  readonly slots = input.required<readonly ScheduleSlot[]>();

  protected readonly days = WORK_DAYS;
  protected readonly dayLabels = DAY_LABELS;
  protected readonly formatRange = formatRange;
  protected readonly byDay = computed(() => slotsByDay(this.slots()));
  protected readonly hours = computed(() => weeklyHours(this.slots()));
}
