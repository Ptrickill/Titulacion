import { BadgeTone } from '../../../shared/ui/status-badge';
import { ClinicalHistoryRequest, Severity, TreatmentStatus } from './clinical-record-api';

/** Habits and medical history of the clinical record, in the order of the form and the record view. */
export const CLINICAL_HISTORY_FIELDS: readonly {
  name: keyof ClinicalHistoryRequest;
  label: string;
}[] = [
  { name: 'habits', label: 'Hábitos' },
  { name: 'physicalActivity', label: 'Actividad física' },
  { name: 'allergies', label: 'Alergias' },
  { name: 'currentMedication', label: 'Medicación actual' },
  { name: 'personalHistory', label: 'Antecedentes personales' },
  { name: 'surgicalHistory', label: 'Antecedentes quirúrgicos' },
  { name: 'familyHistory', label: 'Antecedentes familiares' },
];

/** Avatar initials of a staff member; titles such as "Lcda." or "Dr." are skipped. */
export function initialsOf(fullName: string | null): string {
  const words = (fullName ?? '').split(/\s+/).filter((word) => word && !word.endsWith('.'));
  return words
    .slice(0, 2)
    .map((word) => word.charAt(0).toUpperCase())
    .join('');
}

/** Tone of the initial EVA: 7 or more is severe pain, 4 to 6 moderate. */
export function painScoreTone(painScore: number): BadgeTone {
  if (painScore >= 7) {
    return 'danger';
  }
  return painScore >= 4 ? 'warning' : 'success';
}

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
