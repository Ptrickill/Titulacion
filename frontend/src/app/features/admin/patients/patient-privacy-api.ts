import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ConsentChannel, ConsentType, PatientConsentView } from '../../../shared/legal/legal-api';
import {
  DataRequest,
  DataRequestStatus,
  DataRequestType,
} from '../../../shared/legal/privacy-labels';

/** One consent of the history of a patient (MEJ-06), never deleted. */
export interface ConsentHistoryEntry {
  id: number;
  type: ConsentType;
  textVersion: string;
  channel: ConsentChannel;
  grantedAt: string;
  replacedAt: string | null;
  revokedAt: string | null;
  revocationChannel: ConsentChannel | null;
}

/** GET /api/patients/{id}/consents: the status of each type and the history. */
export interface PatientConsents {
  consents: PatientConsentView[];
  history: ConsentHistoryEntry[];
}

export type ExportFormat = 'pdf' | 'json';

/**
 * The reception's API of the rights of the data subject (MEJ-06): the consents of a patient, its requests and the
 * export of its personal data (never clinical data).
 */
@Injectable({ providedIn: 'root' })
export class PatientPrivacyApi {
  private readonly http = inject(HttpClient);

  consents(patientId: number): Observable<PatientConsents> {
    return this.http.get<PatientConsents>(`/api/patients/${patientId}/consents`);
  }

  /** Records a consent signed on paper, for the version of the text that was shown. */
  grant(patientId: number, type: ConsentType, version: string): Observable<PatientConsents> {
    return this.http.post<PatientConsents>(`/api/patients/${patientId}/consents`, {
      type,
      version,
    });
  }

  revoke(patientId: number, type: ConsentType): Observable<PatientConsents> {
    return this.http.post<PatientConsents>(
      `/api/patients/${patientId}/consents/${type}/revocation`,
      null,
    );
  }

  requests(patientId: number): Observable<DataRequest[]> {
    return this.http.get<DataRequest[]>(`/api/patients/${patientId}/data-requests`);
  }

  registerRequest(
    patientId: number,
    type: DataRequestType,
    receivedOn: string,
  ): Observable<DataRequest> {
    return this.http.post<DataRequest>(`/api/patients/${patientId}/data-requests`, {
      type,
      receivedOn,
    });
  }

  changeRequest(
    patientId: number,
    requestId: number,
    status: DataRequestStatus,
    response: string | null,
  ): Observable<DataRequest> {
    return this.http.patch<DataRequest>(`/api/patients/${patientId}/data-requests/${requestId}`, {
      status,
      response,
    });
  }

  /** The file with the personal data, the consents and the requests (audited as EXPORT by the API). */
  exportData(patientId: number, format: ExportFormat): Observable<Blob> {
    return this.http.get(`/api/patients/${patientId}/data-export.${format}`, {
      responseType: 'blob',
    });
  }
}
