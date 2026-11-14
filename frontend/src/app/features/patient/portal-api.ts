import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
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
}
