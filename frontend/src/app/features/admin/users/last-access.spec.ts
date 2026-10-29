import { formatLastAccess } from './last-access';

describe('formatLastAccess', () => {
  const now = new Date(2026, 9, 29, 10, 30);
  const at = (day: number, hours: number, minutes: number) =>
    new Date(2026, 9, day, hours, minutes).toISOString();

  it('says "Nunca" when the user never logged in', () => {
    expect(formatLastAccess(null, now)).toBe('Nunca');
  });

  it('uses "Hoy" and "Ayer" with the time for the last two calendar days', () => {
    expect(formatLastAccess(at(29, 8, 5), now)).toBe('Hoy, 08:05');
    expect(formatLastAccess(at(28, 23, 59), now)).toBe('Ayer, 23:59');
  });

  it('counts days within the last week and shows the date for older logins', () => {
    expect(formatLastAccess(at(27, 9, 0), now)).toBe('Hace 2 días');
    expect(formatLastAccess(at(23, 9, 0), now)).toBe('Hace 6 días');
    expect(formatLastAccess(at(12, 9, 0), now)).toBe('12/10/2026');
  });
});
