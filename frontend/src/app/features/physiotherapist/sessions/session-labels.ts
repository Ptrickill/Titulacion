import { BadgeTone } from '../../../shared/ui/status-badge';
import { ProgressStatus, SessionStatus } from './therapy-session-api';

// Shared with the patient portal (HU-13), which must not import from the physiotherapist area
export {
  SESSION_STATUS_LABELS,
  SESSION_STATUS_TONES,
} from '../../../shared/clinical/session-labels';

export const SESSION_STATUSES: readonly SessionStatus[] = ['COMPLETED', 'RESCHEDULED', 'NO_SHOW'];

/** Longer text of the options of the form (prototype 06). */
export const SESSION_STATUS_OPTIONS: Record<SessionStatus, string> = {
  COMPLETED: 'Realizada (Sesión completada normalmente)',
  RESCHEDULED: 'Reprogramada (Se cambió la fecha de la sesión)',
  NO_SHOW: 'No asistió (El paciente no se presentó)',
};

export const PROGRESS_STATUSES: readonly ProgressStatus[] = [
  'IMPROVING',
  'STABLE',
  'NEEDS_ATTENTION',
];

export const PROGRESS_STATUS_LABELS: Record<ProgressStatus, string> = {
  IMPROVING: 'En mejoría',
  STABLE: 'Estable',
  NEEDS_ATTENTION: 'Requiere atención',
};

export const PROGRESS_STATUS_TONES: Record<ProgressStatus, BadgeTone> = {
  IMPROVING: 'success',
  STABLE: 'info',
  NEEDS_ATTENTION: 'warning',
};

/** EVA values 0 to 10 for the select of the form. */
export const PAIN_SCORES: readonly number[] = Array.from({ length: 11 }, (_, score) => score);

/** Intensity of an EVA value, as in the options of prototype 06 ("4 - Dolor moderado"). */
export function painScoreLabel(painScore: number): string {
  if (painScore === 0) {
    return 'Sin dolor';
  }
  if (painScore <= 3) {
    return 'Dolor leve';
  }
  if (painScore <= 6) {
    return 'Dolor moderado';
  }
  return painScore <= 9 ? 'Dolor intenso' : 'Dolor máximo';
}
