import { slotProblems, slotsByDay, weeklyHours } from './schedule-rules';
import { slot } from './schedule-testing';

describe('schedule rules (MEJ-04)', () => {
  const problems = (...slots: ReturnType<typeof slot>[]) => slotProblems(slots, '07:00', '20:00');

  it('accepts slots within the clinic hours, and contiguous slots (the API merges them)', () => {
    expect(
      problems(
        slot('MONDAY', '07:00', '12:00'),
        slot('MONDAY', '12:00', '14:00'),
        slot('TUESDAY', '08:00', '20:00'),
      ),
    ).toEqual([null, null, null]);
  });

  it('marks both overlapping slots of the same day, naming the other one', () => {
    expect(problems(slot('MONDAY', '08:00', '12:00'), slot('MONDAY', '11:00', '13:00'))).toEqual([
      'Se superpone con la franja de 11:00 – 13:00 del lunes.',
      'Se superpone con la franja de 08:00 – 12:00 del lunes.',
    ]);
    // The same hours on different days do not overlap
    expect(problems(slot('MONDAY', '08:00', '12:00'), slot('FRIDAY', '08:00', '12:00'))).toEqual([
      null,
      null,
    ]);
  });

  it('marks inverted slots and slots outside the clinic hours', () => {
    expect(
      problems(
        slot('MONDAY', '12:00', '12:00'),
        slot('MONDAY', '15:00', '09:00'),
        slot('TUESDAY', '06:30', '09:00'),
        slot('WEDNESDAY', '19:00', '20:30'),
      ),
    ).toEqual([
      'La hora de inicio debe ser anterior a la de fin.',
      'La hora de inicio debe ser anterior a la de fin.',
      'Fuera de la jornada de la clínica (07:00 a 20:00).',
      'Fuera de la jornada de la clínica (07:00 a 20:00).',
    ]);
  });

  it('skips the rows that are not complete yet', () => {
    expect(slotProblems([{ day: 'MONDAY', start: '08:00' }], '07:00', '20:00')).toEqual([null]);
  });

  it('groups the slots by day in order and adds the weekly hours', () => {
    const slots = [
      slot('MONDAY', '14:00', '17:00'),
      slot('MONDAY', '08:00', '13:00'),
      slot('SATURDAY', '08:00', '09:30'),
    ];

    const byDay = slotsByDay(slots);
    expect(byDay.MONDAY.map((s) => s.start)).toEqual(['08:00', '14:00']);
    expect(byDay.TUESDAY).toEqual([]);
    expect(Object.keys(byDay)).toEqual([
      'MONDAY',
      'TUESDAY',
      'WEDNESDAY',
      'THURSDAY',
      'FRIDAY',
      'SATURDAY',
    ]);
    expect(weeklyHours(slots)).toBe(9.5);
  });
});
