import { Page } from '../../../core/http/page';
import {
  PainPoint,
  TherapySession,
  TherapySessionVersion,
  TherapyTimeline,
} from './therapy-session-api';

/**
 * jsdom has no canvas: Chart.js gets a 2D context whose methods do nothing (measureText measures 0, gradients
 * accept stops), so a chart is created and keeps its data and options for the assertions. Returns the function that
 * restores the original getContext.
 */
export function stubCanvas(): () => void {
  const original = HTMLCanvasElement.prototype.getContext;
  const noop = () => undefined;
  HTMLCanvasElement.prototype.getContext = function (this: HTMLCanvasElement) {
    const state: Record<PropertyKey, unknown> = { canvas: this };
    return new Proxy(state, {
      get: (target, property) => {
        if (property in target) {
          return target[property];
        }
        // Chart.js reads `length` to tell an array of canvases from a context: not a method
        if (typeof property === 'symbol' || property === 'length') {
          return undefined;
        }
        switch (property) {
          case 'measureText':
            return () => ({ width: 0, actualBoundingBoxAscent: 0, actualBoundingBoxDescent: 0 });
          case 'getLineDash':
            return () => [];
          case 'createLinearGradient':
          case 'createRadialGradient':
          case 'createPattern':
            return () => ({ addColorStop: noop });
          default:
            return noop;
        }
      },
      set: (target, property, value) => {
        target[property] = value;
        return true;
      },
    });
  } as unknown as typeof HTMLCanvasElement.prototype.getContext;
  // A responsive chart watches its container
  const withoutResizeObserver = !('ResizeObserver' in globalThis);
  if (withoutResizeObserver) {
    (globalThis as Record<string, unknown>)['ResizeObserver'] = class {
      observe = noop;
      unobserve = noop;
      disconnect = noop;
    };
  }
  return () => {
    HTMLCanvasElement.prototype.getContext = original;
    if (withoutResizeObserver) {
      delete (globalThis as Record<string, unknown>)['ResizeObserver'];
    }
  };
}

/** Initial evaluation and two completed sessions of treatment 30. */
export function fakePainCurve(): PainPoint[] {
  return [
    {
      label: 'Evaluación inicial',
      date: '2026-10-01',
      painScore: 8,
      sessionId: null,
      sequenceNumber: null,
    },
    { label: 'Sesión 1', date: '2026-10-03', painScore: 6, sessionId: 90, sequenceNumber: 1 },
    { label: 'Sesión 3', date: '2026-10-08', painScore: 3, sessionId: 92, sequenceNumber: 3 },
  ];
}

/**
 * Timeline of treatment 30 of patient 15: session 1 by physiotherapist 7, session 2 (no show) and session 3 by
 * physiotherapist 8, in chronological order.
 */
export function fakeTimeline(overrides: Partial<TherapyTimeline> = {}): TherapyTimeline {
  return {
    treatment: {
      id: 30,
      patientId: 15,
      active: true,
      startDate: '2026-10-01',
      initialPainScore: 8,
      plannedSessions: 12,
      completedSessions: 2,
      diagnosis: { code: 'M51.2', description: 'Hernia discal lumbar' },
    },
    from: null,
    to: null,
    sessions: [
      fakeSession(90, { sessionDate: '2026-10-03', painScore: 6 }),
      fakeSession(91, {
        sequenceNumber: 2,
        sessionDate: '2026-10-06',
        painScore: 6,
        status: 'NO_SHOW',
        progressStatus: 'STABLE',
        patientInstructions: null,
        responsible: { id: 8, fullName: 'Lcdo. Mateo Ríos' },
      }),
      fakeSession(92, {
        sequenceNumber: 3,
        sessionDate: '2026-10-08',
        painScore: 3,
        sessionType: 'Terapia manual y movilidad lumbar',
        responsible: { id: 8, fullName: 'Lcdo. Mateo Ríos' },
      }),
    ],
    painCurve: fakePainCurve(),
    ...overrides,
  };
}

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
