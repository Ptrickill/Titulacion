import { ScheduleSlot, StaffSchedule, WorkDay } from './schedule-api';

/** A slot, e.g. slot('MONDAY', '08:00', '13:00'). */
export function slot(day: WorkDay, start: string, end: string): ScheduleSlot {
  return { day, start, end };
}

/** Andrade's synthetic week: mornings and afternoons from Monday to Friday, as in the dev seed. */
export function fakeSchedule(
  staffId: number,
  overrides: Partial<StaffSchedule> = {},
): StaffSchedule {
  return {
    staffId,
    fullName: 'Valeria Andrade Mora',
    openingTime: '07:00',
    closingTime: '20:00',
    slots: [
      slot('MONDAY', '08:00', '13:00'),
      slot('MONDAY', '14:00', '17:00'),
      slot('WEDNESDAY', '08:00', '13:00'),
      slot('FRIDAY', '14:00', '17:00'),
    ],
    ...overrides,
  };
}
