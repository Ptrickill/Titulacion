import { HttpClient } from '@angular/common/http';
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
  /** A new email waits for its owner's confirmation (MEJ-02). */
  emailChangePending: boolean;
  /** ISO-8601 instant, or null if the user never logged in. */
  lastLoginAt: string | null;
  createdAt: string;
}

/**
 * Actions on an account (administrator only), used from "Registro de Usuarios" and the records of its people
 * (MEJ-02). Accounts are created only from the registry and listed there: there is no "create" nor "list" here.
 */
@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);

  /** PATCH /api/users/{id}/status (HU-03); answers the updated account. */
  changeStatus(id: number, active: boolean): Observable<UserAccount> {
    return this.http.patch<UserAccount>(`/api/users/${id}/status`, { active });
  }

  /**
   * POST /api/users/{id}/resend-invitation: a new activation link for a pending account (MEJ-01), or a new link to
   * confirm the new email of an account with a pending change (MEJ-02).
   */
  resendInvitation(id: number): Observable<UserAccount> {
    return this.http.post<UserAccount>(`/api/users/${id}/resend-invitation`, null);
  }
}
