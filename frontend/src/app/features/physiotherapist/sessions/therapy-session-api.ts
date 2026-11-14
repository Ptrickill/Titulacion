import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../../core/http/page';
import type { SessionStatus } from '../../../shared/clinical/session-labels';

export type { SessionStatus };
export type ProgressStatus = 'STABLE' | 'IMPROVING' | 'NEEDS_ATTENTION';

/**
 * Body of POST /api/treatments/{id}/therapy-sessions and PUT /api/therapy-sessions/{id} (HU-10). There is no
 * responsible nor sequence number: the server assigns both.
 */
export interface TherapySessionRequest {
  /** `yyyy-MM-dd`, not after today. */
  sessionDate: string;
  /** `HH:mm`. */
  sessionTime: string;
  sessionType: string;
  activitiesPerformed: string;
  /** Internal note for the physiotherapy team. */
  clinicalObservations: string;
  /** Optional; null when empty. */
  patientInstructions: string | null;
  /** EVA, 0 to 10. */
  painScore: number;
  progressStatus: ProgressStatus;
  status: SessionStatus;
}

/** A staff member as the sessions API returns it; the name is null if the account no longer exists. */
export interface StaffRef {
  id: number;
  fullName: string | null;
}

export interface TherapySession extends Omit<TherapySessionRequest, 'patientInstructions'> {
  id: number;
  treatmentId: number;
  /** Assigned by the server; never changes. */
  sequenceNumber: number;
  patientInstructions: string | null;
  /** The physiotherapist who registered the session, the only one who may correct it. */
  responsible: StaffRef;
  createdAt: string;
  updatedBy: number | null;
  updatedAt: string | null;
}

/** One version kept by Hibernate Envers: the session as it was after that revision. */
export interface TherapySessionVersion {
  revision: number;
  /** CREATE: registration; UPDATE: correction. */
  type: 'CREATE' | 'UPDATE' | 'DELETE';
  changedAt: string;
  /** Null when there was no authenticated user. */
  changedBy: StaffRef | null;
  session: TherapySession;
}

/** A point of the pain curve (HU-11): the initial evaluation or a COMPLETED session. */
export interface PainPoint {
  /** "Evaluación inicial" or "Sesión N". */
  label: string;
  /** `yyyy-MM-dd`. */
  date: string;
  painScore: number;
  /** Null for the initial evaluation. */
  sessionId: number | null;
  sequenceNumber: number | null;
}

/** Starting point and plan of the treatment of a timeline. */
export interface TimelineTreatment {
  id: number;
  patientId: number;
  /** Sessions are only registered on active treatments. */
  active: boolean;
  startDate: string;
  initialPainScore: number;
  plannedSessions: number;
  /** COMPLETED sessions of the whole treatment, whatever the filter. */
  completedSessions: number;
  diagnosis: { code: string; description: string };
}

/** GET /api/treatments/{id}/timeline (HU-11). */
export interface TherapyTimeline {
  treatment: TimelineTreatment;
  from: string | null;
  to: string | null;
  /** Chronological order: date, time and sequence number. */
  sessions: TherapySession[];
  /** The initial evaluation followed by the COMPLETED sessions of the range. */
  painCurve: PainPoint[];
}

/** Optional days (`yyyy-MM-dd`, Quito), both included. */
export interface TimelineRange {
  from: string | null;
  to: string | null;
}

/** Same limit as the backend for the free-text columns. */
export const SESSION_TEXT_MAX = 4000;
export const SESSION_TYPE_MAX = 50;

/** Therapy sessions of a treatment (HU-10); the server audits every read and change. */
@Injectable({ providedIn: 'root' })
export class TherapySessionApi {
  private readonly http = inject(HttpClient);

  /** Ordered by sequence number. */
  list(treatmentId: number, page: number, size: number): Observable<Page<TherapySession>> {
    return this.http.get<Page<TherapySession>>(`/api/treatments/${treatmentId}/therapy-sessions`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  /** The timeline of the treatment; an empty bound is not sent. The server audits it as READ of the treatment. */
  timeline(treatmentId: number, range: TimelineRange): Observable<TherapyTimeline> {
    let params = new HttpParams();
    if (range.from) {
      params = params.set('from', range.from);
    }
    if (range.to) {
      params = params.set('to', range.to);
    }
    return this.http.get<TherapyTimeline>(`/api/treatments/${treatmentId}/timeline`, { params });
  }

  find(sessionId: number): Observable<TherapySession> {
    return this.http.get<TherapySession>(`/api/therapy-sessions/${sessionId}`);
  }

  register(treatmentId: number, request: TherapySessionRequest): Observable<TherapySession> {
    return this.http.post<TherapySession>(
      `/api/treatments/${treatmentId}/therapy-sessions`,
      request,
    );
  }

  correct(sessionId: number, request: TherapySessionRequest): Observable<TherapySession> {
    return this.http.put<TherapySession>(`/api/therapy-sessions/${sessionId}`, request);
  }

  /** Most recent first. */
  versions(sessionId: number): Observable<TherapySessionVersion[]> {
    return this.http.get<TherapySessionVersion[]>(`/api/therapy-sessions/${sessionId}/versions`);
  }
}
