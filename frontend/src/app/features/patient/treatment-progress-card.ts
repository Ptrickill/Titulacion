import { Component, computed, input } from '@angular/core';

const RADIUS = 52;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/**
 * "Progreso del tratamiento" (HU-12, prototype 14): a ring with the sessions that took place against the planned
 * ones, and the percentage of the plan. Read only.
 */
@Component({
  selector: 'app-treatment-progress-card',
  templateUrl: './treatment-progress-card.html',
  styleUrl: './treatment-progress-card.scss',
})
export class TreatmentProgressCard {
  /** Sessions that took place. */
  readonly completed = input.required<number>();
  readonly planned = input.required<number>();
  /** 0 to 100, as the server computes it. */
  readonly percentage = input.required<number>();

  protected readonly radius = RADIUS;
  protected readonly circumference = CIRCUMFERENCE;

  /** Length of the ring that stays empty. */
  protected readonly dashOffset = computed(() => {
    const filled = Math.min(100, Math.max(0, this.percentage())) / 100;
    return CIRCUMFERENCE * (1 - filled);
  });

  protected readonly ringLabel = computed(
    () =>
      `${this.completed()} de ${this.planned()} sesiones realizadas, ${this.percentage()} % del plan`,
  );

  protected readonly sessionsWord = computed(() => (this.planned() === 1 ? 'sesión' : 'sesiones'));
}
