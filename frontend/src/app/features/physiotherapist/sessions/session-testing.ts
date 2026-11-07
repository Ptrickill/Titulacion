import { Page } from '../../../core/http/page';
import { TherapySession, TherapySessionVersion } from './therapy-session-api';

/** Synthetic session of treatment 30, registered by physiotherapist 7, for unit tests. */
export function fakeSession(id: number, overrides: Partial<TherapySession> = {}): TherapySession {
  return {
    id,
    treatmentId: 30,
    sequenceNumber: 1,
    sessionDate: '2026-11-04',
    sessionTime: '09:30:00',
    sessionType: 'Fisioterapia lumbar mecánica',
    activitiesPerformed: 'Movilización lumbar y TENS 15 minutos',
    clinicalObservations: 'Buena tolerancia al ejercicio',
    patientInstructions: 'Calor local 15 minutos en casa',
    painScore: 4,
    progressStatus: 'IMPROVING',
    status: 'COMPLETED',
    responsible: { id: 7, fullName: 'Lcda. Valeria Andrade' },
    createdAt: '2026-11-04T14:40:00Z',
    updatedBy: null,
    updatedAt: null,
    ...overrides,
  };
}

export function pageOf<T>(content: T[], totalElements = content.length, page = 0): Page<T> {
  return {
    content,
    page,
    size: 10,
    totalElements,
    totalPages: Math.max(1, Math.ceil(totalElements / 10)),
  };
}

/** Registration and one correction of session 90, most recent first. */
export function fakeVersions(): TherapySessionVersion[] {
  const author = { id: 7, fullName: 'Lcda. Valeria Andrade' };
  return [
    {
      revision: 12,
      type: 'UPDATE',
      changedAt: '2026-11-05T16:10:00Z',
      changedBy: author,
      session: fakeSession(90, { painScore: 3, activitiesPerformed: 'Ejercicios de Williams' }),
    },
    {
      revision: 10,
      type: 'CREATE',
      changedAt: '2026-11-04T14:40:00Z',
      changedBy: author,
      session: fakeSession(90),
    },
  ];
}
