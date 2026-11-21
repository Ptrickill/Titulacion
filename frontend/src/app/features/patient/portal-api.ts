import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SessionStatus } from '../../shared/clinical/session-labels';
import { TreatmentStatus } from '../../shared/clinical/treatment-labels';

/** A treatment of the authenticated patient (GET /api/portal/treatments, HU-12). */
export interface PortalTreatment {
  id: number;
  diagnosis: { code: string; description: string };
  status: TreatmentStatus;
  active: boolean;
  /** `yyyy-MM-dd`: the day of the initial evaluation. */
  startDate: string;
  /** Current responsible physiotherapist; null if the treatment has none. */
  responsibleName: string | null;
  plannedSessions: number;
  /** Sessions that took place (COMPLETED). */
  completedSessions: number;
}

/** A point of the pain evolution: the initial evaluation or a session that took place. */
export interface PortalPainPoint {
  /** "Evaluación inicial" or "Sesión N". */
  label: string;
  /** `yyyy-MM-dd`. */
  date: string;
  /** EVA, 0 to 10. */
  painScore: number;
  /** Null for the initial evaluation. */
  sequenceNumber: number | null;
}

/** Progress of one treatment (GET /api/portal/treatments/{id}/progress). */
export interface TreatmentProgress {
  treatmentId: number;
  completedSessions: number;
  plannedSessions: number;
  /** Completed over planned, rounded down, 0 to 100. */
  percentage: number;
  initialPainScore: number;
  /** EVA of the last session that took place; null when there is none yet. */
  lastPainScore: number | null;
  /** Initial EVA minus the last one: positive when the pain went down. Null without sessions. */
  painReduction: number | null;
  /** Initial evaluation followed by the sessions that took place, in chronological order. */
  painCurve: PortalPainPoint[];
}

/**
 * A session as its patient reads it (GET /api/portal/treatments/{id}/sessions, HU-13). The API never sends the
 * clinical observations nor the progress status, which are internal notes of the physiotherapy team.
 */
export interface PortalSession {
  sequenceNumber: number;
  /** `yyyy-MM-dd`. */
  sessionDate: string;
  /** `HH:mm:ss`. */
  sessionTime: string;
  sessionType: string;
  /** Physiotherapist who registered the session; null if the account no longer exists. */
  responsibleName: string | null;
  /** EVA, 0 to 10. */
  painScore: number;
  activitiesPerformed: string;
  status: SessionStatus;
  /** Instructions for the patient; null when the session has none. */
  patientInstructions: string | null;
}

/** The last instructions written for the patient (GET /api/portal/treatments/{id}/instructions). */
export interface PortalInstruction {
  instructions: string;
  /** `yyyy-MM-dd`: day of the session they were written in. */
  sessionDate: string;
  sequenceNumber: number;
  responsibleName: string | null;
}

/**
 * Read-only patient portal. The server takes the patient from the authenticated account, so no call carries a
 * patient id; a treatment of another patient answers 403.
 */
@Injectable({ providedIn: 'root' })
export class PortalApi {
  private readonly http = inject(HttpClient);

  /** The active treatment first, then the others from the most recent to the oldest. */
  treatments(): Observable<PortalTreatment[]> {
    return this.http.get<PortalTreatment[]>('/api/portal/treatments');
  }

  progress(treatmentId: number): Observable<TreatmentProgress> {
    return this.http.get<TreatmentProgress>(`/api/portal/treatments/${treatmentId}/progress`);
  }

  /** Every session of the treatment in chronological order, whatever its status. */
  sessions(treatmentId: number): Observable<PortalSession[]> {
    return this.http.get<PortalSession[]>(`/api/portal/treatments/${treatmentId}/sessions`);
  }

  /** The last instructions for the patient; null when no session has any (200 without a body). */
  instructions(treatmentId: number): Observable<PortalInstruction | null> {
    return this.http.get<PortalInstruction | null>(
      `/api/portal/treatments/${treatmentId}/instructions`,
    );
  }
}
