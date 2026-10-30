import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Treatment, TreatmentStatus } from './clinical-record-api';

/** Period of a physiotherapist as responsible of a treatment; the current one has no end. */
export interface ResponsiblePeriod {
  physiotherapistId: number;
  fullName: string | null;
  validFrom: string;
  validTo: string | null;
}

/** Status change of a treatment with its reason, user and date. */
export interface StatusChange {
  id: number;
  previousStatus: TreatmentStatus;
  newStatus: TreatmentStatus;
  reason: string;
  changedById: number;
  changedByName: string | null;
  changedAt: string;
}

/** GET /api/treatments/{id}/history: status, allowed transitions and both histories, most recent first. */
export interface TreatmentHistory {
  treatmentId: number;
  status: TreatmentStatus;
  /** ACTIVE → COMPLETED or ABANDONED; ABANDONED → ACTIVE; COMPLETED is final. */
  allowedNextStatuses: TreatmentStatus[];
  currentResponsible: ResponsiblePeriod | null;
  responsibles: ResponsiblePeriod[];
  statusChanges: StatusChange[];
}

/** Active physiotherapist who can become responsible of a treatment. */
export interface Physiotherapist {
  id: number;
  fullName: string;
}

export interface ChangeTreatmentStatusRequest {
  newStatus: TreatmentStatus;
  reason: string;
}

/** Same limit as the backend for the reason of a status change. */
export const STATUS_CHANGE_REASON_MAX = 1000;

/** Traceability of a treatment (HU-08): responsible physiotherapist and status, with their histories. */
@Injectable({ providedIn: 'root' })
export class TreatmentApi {
  private readonly http = inject(HttpClient);

  find(treatmentId: number): Observable<Treatment> {
    return this.http.get<Treatment>(`/api/treatments/${treatmentId}`);
  }

  history(treatmentId: number): Observable<TreatmentHistory> {
    return this.http.get<TreatmentHistory>(`/api/treatments/${treatmentId}/history`);
  }

  changeResponsible(treatmentId: number, physiotherapistId: number): Observable<TreatmentHistory> {
    return this.http.put<TreatmentHistory>(`/api/treatments/${treatmentId}/responsible`, {
      physiotherapistId,
    });
  }

  changeStatus(
    treatmentId: number,
    request: ChangeTreatmentStatusRequest,
  ): Observable<TreatmentHistory> {
    return this.http.patch<TreatmentHistory>(`/api/treatments/${treatmentId}/status`, request);
  }

  listPhysiotherapists(): Observable<Physiotherapist[]> {
    return this.http.get<Physiotherapist[]>('/api/physio/physiotherapists');
  }
}
