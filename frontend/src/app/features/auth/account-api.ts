import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

/** Body of POST /api/auth/activate and POST /api/auth/reset-password (MEJ-01). */
export interface SetPasswordRequest {
  token: string;
  password: string;
  passwordConfirmation: string;
}

/** Answer of POST /api/auth/forgot-password: always the same message, whether or not the account exists. */
export interface ForgotPasswordResponse {
  message: string;
}

/** Public account links (MEJ-01): no session is needed for any of them. */
@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly http = inject(HttpClient);

  activate(request: SetPasswordRequest): Observable<void> {
    return this.http.post<void>('/api/auth/activate', request);
  }

  resetPassword(request: SetPasswordRequest): Observable<void> {
    return this.http.post<void>('/api/auth/reset-password', request);
  }

  forgotPassword(email: string): Observable<ForgotPasswordResponse> {
    return this.http.post<ForgotPasswordResponse>('/api/auth/forgot-password', { email });
  }
}
