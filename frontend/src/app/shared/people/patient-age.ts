/** Today's date in the browser's time zone as yyyy-MM-dd (the value format of `<input type="date">`). */
export function todayIsoDate(now: Date = new Date()): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * Completed years between two yyyy-MM-dd dates, as the backend calculates them; null if the birth date is
 * missing or after the reference date. Compared as text parts, so time zones never shift a day.
 */
export function ageOn(birthDate: string | null | undefined, today: string): number | null {
  const birth = /^(\d{4})-(\d{2})-(\d{2})$/.exec(birthDate ?? '');
  const ref = /^(\d{4})-(\d{2})-(\d{2})$/.exec(today);
  if (!birth || !ref || birthDate! > today) {
    return null;
  }
  const [, by, bm, bd] = birth.map(Number);
  const [, ty, tm, td] = ref.map(Number);
  const hadBirthday = tm > bm || (tm === bm && td >= bd);
  return ty - by - (hadBirthday ? 0 : 1);
}

export function formatAge(age: number | null): string {
  if (age === null) {
    return '';
  }
  return age === 1 ? '1 año' : `${age} años`;
}
