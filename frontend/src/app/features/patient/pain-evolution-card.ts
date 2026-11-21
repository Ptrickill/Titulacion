import { formatDate } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { APP_LOCALE } from '../../core/locale';
import { painScoreTone } from '../../shared/clinical/treatment-labels';
import { BadgeTone, StatusBadge } from '../../shared/ui/status-badge';
import { PortalPainPoint } from './portal-api';

interface Bar {
  label: string;
  painScore: number;
  /** Width of the bar: the EVA over 10. */
  width: string;
  tone: BadgeTone;
}

/** What happened to the pain between the initial evaluation and the last session. */
interface Change {
  badge: string;
  tone: BadgeTone;
  icon: string;
}

function points(count: number): string {
  return count === 1 ? '1 punto' : `${count} puntos`;
}

/**
 * "Evolución del dolor (escala EVA)" (HU-12, prototype 14): one bar per point of the pain curve (the initial
 * evaluation and each session that took place) and the reduction achieved. Read only.
 */
@Component({
  selector: 'app-pain-evolution-card',
  imports: [MatIconModule, StatusBadge],
  templateUrl: './pain-evolution-card.html',
  styleUrl: './pain-evolution-card.scss',
})
export class PainEvolutionCard {
  /** The initial evaluation followed by the sessions that took place, in chronological order. */
  readonly curve = input.required<PortalPainPoint[]>();
  readonly initialPainScore = input.required<number>();
  /** Null when no session has taken place. */
  readonly lastPainScore = input.required<number | null>();
  /** Initial EVA minus the last one; null when no session has taken place. */
  readonly painReduction = input.required<number | null>();

  protected readonly bars = computed<Bar[]>(() =>
    this.curve().map((point) => ({
      label: `${this.nameOf(point)} (${formatDate(point.date, "dd 'de' MMMM", APP_LOCALE)})`,
      painScore: point.painScore,
      width: `${Math.min(10, Math.max(0, point.painScore)) * 10}%`,
      tone: painScoreTone(point.painScore),
    })),
  );

  protected readonly hasSessions = computed(() => this.painReduction() !== null);

  protected readonly change = computed<Change | null>(() => {
    const reduction = this.painReduction();
    const last = this.lastPainScore();
    if (reduction === null || last === null) {
      return null;
    }
    const range = `de ${this.initialPainScore()}/10 a ${last}/10`;
    if (reduction > 0) {
      return { badge: `Disminución ${range}`, tone: 'teal', icon: 'trending_down' };
    }
    if (reduction < 0) {
      return { badge: `Aumento ${range}`, tone: 'warning', icon: 'trending_up' };
    }
    return { badge: `Sin cambios: ${last}/10`, tone: 'neutral', icon: 'trending_flat' };
  });

  /** Absolute change in points, e.g. "3 puntos". */
  protected readonly changeInPoints = computed(() => points(Math.abs(this.painReduction() ?? 0)));

  /** "Sesión 01", as in the prototype; the initial evaluation keeps its label. */
  private nameOf(point: PortalPainPoint): string {
    return point.sequenceNumber === null
      ? point.label
      : `Sesión ${String(point.sequenceNumber).padStart(2, '0')}`;
  }
}
