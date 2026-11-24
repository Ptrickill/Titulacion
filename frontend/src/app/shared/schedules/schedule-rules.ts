import { ScheduleSlot, WorkDay } from './schedule-api';

/** Monday to Saturday, in order. */
export const WORK_DAYS: readonly WorkDay[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
];

export const DAY_LABELS: Record<WorkDay, string> = {
  MONDAY: 'Lunes',
  TUESDAY: 'Martes',
  WEDNESDAY: 'Miércoles',
  THURSDAY: 'Jueves',
  FRIDAY: 'Viernes',
  SATURDAY: 'Sábado',
};

export const DAY_SHORT_LABELS: Record<WorkDay, string> = {
  MONDAY: 'Lun',
  TUESDAY: 'Mar',
  WEDNESDAY: 'Mié',
  THURSDAY: 'Jue',
  FRIDAY: 'Vie',
  SATURDAY: 'Sáb',
};

/** "08:00 – 12:00" */
export function formatRange(slot: { start: string; end: string }): string {
  return `${slot.start} – ${slot.end}`;
}

/** Minutes since midnight of an HH:mm time. */
export function minutesOf(time: string): number {
  const [hours, minutes] = time.split(':').map(Number);
  return hours * 60 + minutes;
}

/** The slots of each day, ordered by start time; every day is a key. */
export function slotsByDay(slots: readonly ScheduleSlot[]): Record<WorkDay, ScheduleSlot[]> {
  const byDay = Object.fromEntries(WORK_DAYS.map((day) => [day, [] as ScheduleSlot[]])) as Record<
    WorkDay,
    ScheduleSlot[]
  >;
  for (const slot of slots) {
    byDay[slot.day]?.push(slot);
  }
  for (const day of WORK_DAYS) {
    byDay[day].sort((a, b) => minutesOf(a.start) - minutesOf(b.start));
  }
  return byDay;
}

/** Hours per week, e.g. 40 or 37.5. */
export function weeklyHours(slots: readonly ScheduleSlot[]): number {
  const minutes = slots.reduce(
    (total, slot) => total + minutesOf(slot.end) - minutesOf(slot.start),
    0,
  );
  return Math.round((minutes / 60) * 10) / 10;
}

/**
 * The problem of each slot (null when it has none), with the rules of the API: the start before the end, within the
 * clinic hours, and no overlap with another slot of the same day. Contiguous slots (08:00-12:00 and 12:00-14:00) are
 * not a problem: the API merges them. Slots with a missing value are skipped (the form marks them as required).
 */
export function slotProblems(
  slots: readonly Partial<ScheduleSlot>[],
  openingTime: string,
  closingTime: string,
): (string | null)[] {
  const opening = minutesOf(openingTime);
  const closing = minutesOf(closingTime);
  return slots.map((slot, index) => {
    if (!slot.day || !slot.start || !slot.end) {
      return null;
    }
    const start = minutesOf(slot.start);
    const end = minutesOf(slot.end);
    if (start >= end) {
      return 'La hora de inicio debe ser anterior a la de fin.';
    }
    if (start < opening || end > closing) {
      return `Fuera de la jornada de la clínica (${openingTime} a ${closingTime}).`;
    }
    const overlapping = slots.find(
      (other, otherIndex) =>
        otherIndex !== index &&
        other.day === slot.day &&
        !!other.start &&
        !!other.end &&
        minutesOf(other.start) < end &&
        start < minutesOf(other.end),
    );
    return overlapping
      ? `Se superpone con la franja de ${formatRange(overlapping as ScheduleSlot)} del ${DAY_LABELS[slot.day].toLowerCase()}.`
      : null;
  });
}
