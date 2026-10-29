import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Role } from '../../../core/auth/role';

/** Account as returned by GET /api/users (never includes the password). */
export interface UserAccount {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  active: boolean;
  /** ISO-8601 instant, or null if the user never logged in. */
  lastLoginAt: string | null;
  createdAt: string;
}

/** Body of POST /api/users; patientId only for the PATIENT role. */
export interface CreateUserRequest {
  email: string;
  fullName: string;
  role: Role;
  password: string;
  patientId: number | null;
}

/** Patient without a portal account (GET /api/patients/portal-candidates). */
export interface PortalCandidate {
  id: number;
  /** Surnames first, as in the clinical record. */
  fullName: string;
  nationalId: string;
  medicalRecordNumber: string;
}

/** Account administration endpoints (administrator only). */
@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);

  findAll(): Observable<UserAccount[]> {
    return this.http.get<UserAccount[]>('/api/users');
  }

  create(request: CreateUserRequest): Observable<UserAccount> {
    return this.http.post<UserAccount>('/api/users', request);
  }

  findPortalCandidates(query: string): Observable<PortalCandidate[]> {
    return this.http.get<PortalCandidate[]>('/api/patients/portal-candidates', {
      params: new HttpParams().set('query', query),
    });
  }
}
