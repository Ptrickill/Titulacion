import { BadgeTone } from '../../../shared/ui/status-badge';
import { Severity, TreatmentStatus } from './clinical-record-api';

export const SEVERITIES: readonly Severity[] = ['MILD', 'MODERATE', 'SEVERE'];

export const SEVERITY_LABELS: Record<Severity, string> = {
  MILD: 'Leve',
  MODERATE: 'Moderada',
  SEVERE: 'Grave',
};

/** Text color class of each severity, as in prototype 03 (green, amber, red). */
export const SEVERITY_TONES: Record<Severity, BadgeTone> = {
  MILD: 'success',
  MODERATE: 'warning',
  SEVERE: 'danger',
};

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
