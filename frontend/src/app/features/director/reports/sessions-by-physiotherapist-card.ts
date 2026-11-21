import { formatNumber } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { APP_LOCALE } from '../../../core/locale';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { PhysiotherapistSessions } from './report-api';

interface PhysiotherapistRow {
  physiotherapistId: number;
  name: string;
  initials: string;
  sessions: number;
  unit: string;
  /** Width of the bar: the sessions against those of the physiotherapist with the most, in percent. */
  width: number;
}

/** Initials of the first two words of a name, skipping titles such as "Lcda." or "Dr.". */
export function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter((word) => word && !word.endsWith('.'))
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join('');
}

/**
 * "Sesiones por fisioterapeuta" (HU-14, prototype 13): the sessions each physiotherapist registered, from the most
 * to the fewest, with a bar against the one with the most, and the average among them. Read only.
 */
@Component({
  selector: 'app-sessions-by-physiotherapist-card',
  imports: [StatusBadge],
  templateUrl: './sessions-by-physiotherapist-card.html',
  styleUrl: './sessions-by-physiotherapist-card.scss',
})
export class SessionsByPhysiotherapistCard {
  /** In the order of the API: from the most sessions to the fewest. */
  readonly sessions = input.required<PhysiotherapistSessions[]>();
  readonly averageSessions = input.required<number>();

  protected readonly average = computed(() =>
    formatNumber(this.averageSessions(), APP_LOCALE, '1.1-1'),
  );

  protected readonly rows = computed<PhysiotherapistRow[]>(() => {
    const rows = this.sessions();
    const most = Math.max(1, ...rows.map((row) => row.sessions));
    return rows.map((row) => {
      const name = row.name ?? 'No disponible';
      return {
        physiotherapistId: row.physiotherapistId,
        name,
        initials: row.name ? initialsOf(row.name) : '?',
        sessions: row.sessions,
        unit: row.sessions === 1 ? 'sesión' : 'sesiones',
        width: Math.round((row.sessions / most) * 100),
      };
    });
  });
}
