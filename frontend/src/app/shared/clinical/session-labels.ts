import { BadgeTone } from '../ui/status-badge';

/** Values of `therapy_sessions.status`. */
export type SessionStatus = 'COMPLETED' | 'RESCHEDULED' | 'NO_SHOW';

export const SESSION_STATUS_LABELS: Record<SessionStatus, string> = {
  COMPLETED: 'Realizada',
  RESCHEDULED: 'Reprogramada',
  NO_SHOW: 'No asistió',
};

export const SESSION_STATUS_TONES: Record<SessionStatus, BadgeTone> = {
  COMPLETED: 'success',
  RESCHEDULED: 'info',
  NO_SHOW: 'neutral',
};
