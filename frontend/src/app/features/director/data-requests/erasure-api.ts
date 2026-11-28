import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DataRequest } from '../../../shared/legal/privacy-labels';

/** An open erasure request with the patient's name and national ID (typed to confirm), MEJ-06. */
export interface ErasureRequest {
  request: DataRequest;
  patientId: number;
  patientName: string;
  nationalId: string;
}

/** The operations director's API of the erasure requests (MEJ-06). */
@Injectable({ providedIn: 'root' })
export class ErasureApi {
  private readonly http = inject(HttpClient);

  openRequests(): Observable<ErasureRequest[]> {
    return this.http.get<ErasureRequest[]>('/api/data-requests/erasures');
  }

  /** Accepts the request, confirmed with the patient's national ID; irreversible. */
  accept(requestId: number, nationalId: string, response: string): Observable<DataRequest> {
    return this.http.post<DataRequest>(`/api/data-requests/erasures/${requestId}/acceptance`, {
      nationalId,
      response,
    });
  }

  reject(requestId: number, response: string): Observable<DataRequest> {
    return this.http.post<DataRequest>(`/api/data-requests/erasures/${requestId}/rejection`, {
      response,
    });
  }
}
