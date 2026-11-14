import { PortalPainPoint, PortalTreatment, TreatmentProgress } from './portal-api';

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
