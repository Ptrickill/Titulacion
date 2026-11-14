import { BadgeTone } from '../ui/status-badge';

/** Values of `treatments.status`. */
export type TreatmentStatus = 'ACTIVE' | 'COMPLETED' | 'ABANDONED';

export const TREATMENT_STATUS_LABELS: Record<TreatmentStatus, string> = {
  ACTIVE: 'Activo',
  COMPLETED: 'Finalizado',
  ABANDONED: 'Abandonado',
};

export const TREATMENT_STATUS_TONES: Record<TreatmentStatus, BadgeTone> = {
  ACTIVE: 'success',
  COMPLETED: 'info',
  ABANDONED: 'neutral',
};

/** Tone of an EVA value: 7 or more is severe pain, 4 to 6 moderate. */
export function painScoreTone(painScore: number): BadgeTone {
  if (painScore >= 7) {
    return 'danger';
  }
  return painScore >= 4 ? 'warning' : 'success';
}
