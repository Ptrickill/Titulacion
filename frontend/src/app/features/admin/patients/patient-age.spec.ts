import { ageOn, formatAge, todayIsoDate } from './patient-age';

describe('patient age', () => {
  it('counts completed years, like the backend', () => {
    expect(ageOn('1996-03-15', '2026-03-14')).toBe(29);
    expect(ageOn('1996-03-15', '2026-03-15')).toBe(30);
    expect(ageOn('1996-03-15', '2026-10-29')).toBe(30);
    expect(ageOn('2026-05-02', '2026-10-29')).toBe(0);
  });

  it('has no age without a birth date or with a future one', () => {
    expect(ageOn('', '2026-10-29')).toBeNull();
    expect(ageOn(null, '2026-10-29')).toBeNull();
    expect(ageOn('2026-10-30', '2026-10-29')).toBeNull();
  });

  it('formats the age in Spanish', () => {
    expect(formatAge(30)).toBe('30 años');
    expect(formatAge(1)).toBe('1 año');
    expect(formatAge(0)).toBe('0 años');
    expect(formatAge(null)).toBe('');
  });

  it('gives today as a local yyyy-MM-dd date', () => {
    expect(todayIsoDate(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });
});
