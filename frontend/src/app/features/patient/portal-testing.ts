import {
  PortalInstruction,
  PortalPainPoint,
  PortalSession,
  PortalTreatment,
  TreatmentProgress,
} from './portal-api';

/** Synthetic data of the patient portal for unit tests. */

export function fakeTreatment(overrides: Partial<PortalTreatment> = {}): PortalTreatment {
  return {
    id: 30,
    diagnosis: { code: 'M54.5', description: 'Lumbago no especificado' },
    status: 'ACTIVE',
    active: true,
    startDate: '2026-10-01',
    responsibleName: 'Lcda. Valeria Andrade',
    plannedSessions: 12,
    completedSessions: 4,
    ...overrides,
  };
}

/** The initial evaluation (EVA 7) and one session for each of the other scores, two days apart. */
export function fakeCurve(...sessionScores: number[]): PortalPainPoint[] {
  return [
    { label: 'Evaluación inicial', date: '2026-10-01', painScore: 7, sequenceNumber: null },
    ...sessionScores.map((painScore, index) => ({
      label: `Sesión ${index + 1}`,
      date: `2026-10-${String(5 + index * 2).padStart(2, '0')}`,
      painScore,
      sequenceNumber: index + 1,
    })),
  ];
}

/** Four sessions of twelve, from EVA 7 down to 4. */
export function fakeProgress(overrides: Partial<TreatmentProgress> = {}): TreatmentProgress {
  return {
    treatmentId: 30,
    completedSessions: 4,
    plannedSessions: 12,
    percentage: 33,
    initialPainScore: 7,
    lastPainScore: 4,
    painReduction: 3,
    painCurve: fakeCurve(7, 6, 5, 4),
    ...overrides,
  };
}

/** Session 1 of 5 October at 08:30: took place, EVA 7, with instructions for the patient. */
export function fakeSession(overrides: Partial<PortalSession> = {}): PortalSession {
  return {
    sequenceNumber: 1,
    sessionDate: '2026-10-05',
    sessionTime: '08:30:00',
    sessionType: 'Terapia manual',
    responsibleName: 'Lcda. Valeria Andrade',
    painScore: 7,
    activitiesPerformed: 'Movilización lumbar y TENS 15 minutos',
    status: 'COMPLETED',
    patientInstructions: 'Calor local 15 minutos',
    ...overrides,
  };
}

export function fakeInstruction(overrides: Partial<PortalInstruction> = {}): PortalInstruction {
  return {
    instructions: 'Continuar la rutina de fortalecimiento domiciliario 3 días por semana.',
    sessionDate: '2026-10-15',
    sequenceNumber: 4,
    responsibleName: 'Lcda. Valeria Andrade',
    ...overrides,
  };
}
