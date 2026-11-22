import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Role } from '../../../core/auth/role';

/**
 * MEJ-01: a new account is pending until its owner follows the emailed activation link; a deactivated account is
 * inactive whether or not it was ever activated.
 */
export type AccountStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'INACTIVE';

/** Account as returned by GET /api/users (never includes the password). */
export interface UserAccount {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  /** Not deactivated by an administrator (HU-03). */
  active: boolean;
  status: AccountStatus;
  /** ISO-8601 instant, or null if the user never logged in. */
  lastLoginAt: string | null;
  createdAt: string;
}

/**
 * Body of POST /api/users; patientId only for the PATIENT role. There is no password: the owner sets it with the
 * activation link sent by email (MEJ-01).
 */
export interface CreateUserRequest {
  email: string;
  fullName: string;
  role: Role;
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

  /** PATCH /api/users/{id}/status (HU-03); answers the updated account. */
  changeStatus(id: number, active: boolean): Observable<UserAccount> {
    return this.http.patch<UserAccount>(`/api/users/${id}/status`, { active });
  }

  /** POST /api/users/{id}/resend-invitation (MEJ-01): a new activation link for a pending account. */
  resendInvitation(id: number): Observable<UserAccount> {
    return this.http.post<UserAccount>(`/api/users/${id}/resend-invitation`, null);
  }

  findPortalCandidates(query: string): Observable<PortalCandidate[]> {
    return this.http.get<PortalCandidate[]>('/api/patients/portal-candidates', {
      params: new HttpParams().set('query', query),
    });
  }
}
