import { Treatment } from './clinical-record-api';
import { TreatmentHistory } from './treatment-api';

/** Synthetic active treatment for unit tests. */
export function fakeTreatment(id: number, overrides: Partial<Treatment> = {}): Treatment {
  return {
    id,
    clinicalRecordId: 40,
    patientId: 15,
    diagnosis: { code: 'M54.4', description: 'Lumbago con ciática' },
    chiefComplaint: 'Dolor lumbar al levantar peso',
    currentIllness: 'Tres semanas de evolución',
    referringPhysician: 'Dr. Carlos Andrade',
    complementaryTests: 'Radiografía lumbar',
    severity: 'MODERATE',
    painType: 'Punzante',
    painLocation: 'Región lumbar',
    initialPainScore: 7,
    painFrequency: 'Intermitente',
    sessionFrequency: '3 veces por semana',
    goal: 'Reducción del dolor y fortalecimiento lumbar progresivo',
    plannedSessions: 12,
    status: 'ACTIVE',
    startDate: '2026-10-01',
    responsiblePhysiotherapistId: 7,
    createdAt: '2026-10-01T15:00:00Z',
    ...overrides,
  };
}

/** History of an active treatment with its first responsible and no status changes. */
export function fakeHistory(
  treatmentId: number,
  overrides: Partial<TreatmentHistory> = {},
): TreatmentHistory {
  const current = {
    physiotherapistId: 7,
    fullName: 'Lcda. Valeria Andrade',
    validFrom: '2026-10-01T15:00:00Z',
    validTo: null,
  };
  return {
    treatmentId,
    status: 'ACTIVE',
    allowedNextStatuses: ['COMPLETED', 'ABANDONED'],
    currentResponsible: current,
    responsibles: [current],
    statusChanges: [],
    ...overrides,
  };
}
