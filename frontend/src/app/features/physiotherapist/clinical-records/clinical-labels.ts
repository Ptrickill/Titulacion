import { BadgeTone } from '../../../shared/ui/status-badge';
import { ClinicalHistoryRequest, Severity } from './clinical-record-api';

// Shared with the patient portal (HU-12), which must not import from the physiotherapist area
export {
  painScoreTone,
  TREATMENT_STATUS_LABELS,
  TREATMENT_STATUS_TONES,
} from '../../../shared/clinical/treatment-labels';

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

/** Initials of a patient in the tables: the first letter of the first names and of the last names. */
export function patientInitials(patient: { firstName: string; lastName: string }): string {
  return `${patient.firstName.charAt(0)}${patient.lastName.charAt(0)}`.toUpperCase();
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

