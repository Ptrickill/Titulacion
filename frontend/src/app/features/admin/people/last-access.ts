import { formatDate } from '@angular/common';
import { APP_LOCALE } from '../../../core/locale';

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Last login in the style of the prototype: "Hoy, 22:58", "Ayer, 15:20", "Hace 3 días", a date for older
 * logins and "Nunca" when the user has not logged in yet. Days are calendar days in the browser's time zone.
 */
export function formatLastAccess(lastLoginAt: string | null, now: Date = new Date()): string {
  if (!lastLoginAt) {
    return 'Nunca';
  }
  const date = new Date(lastLoginAt);
  const days = Math.round((startOfDay(now) - startOfDay(date)) / DAY_MS);
  const time = formatDate(date, 'HH:mm', APP_LOCALE);
  if (days <= 0) {
    return `Hoy, ${time}`;
  }
  if (days === 1) {
    return `Ayer, ${time}`;
  }
  if (days < 7) {
    return `Hace ${days} días`;
  }
  return formatDate(date, 'dd/MM/yyyy', APP_LOCALE);
}

function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}
