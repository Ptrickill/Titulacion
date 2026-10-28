import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { map, Observable, tap } from 'rxjs';
import { Role } from './role';

export interface LoginRequest {
  email: string;
  password: string;
}

/** Expected response of POST /api/auth/login (contract to be confirmed in HU-01). */
export interface LoginResponse {
  token: string;
  role: Role;
  fullName: string;
}

export interface UserSession {
  /** Null only for the development preview session. */
  token: string | null;
  role: Role;
  fullName: string;
}

const STORAGE_KEY = 'rehab-elite.session';

/**
 * Holds the current user session. It lives in sessionStorage, so it ends when the browser tab is closed.
 */
@Injectable({ providedIn: 'root' })
export class AuthSession {
  private readonly http = inject(HttpClient);
  private readonly state = signal<UserSession | null>(readStoredSession());

  readonly session = this.state.asReadonly();
  readonly isAuthenticated = computed(() => this.state() !== null);
  readonly role = computed(() => this.state()?.role ?? null);
  readonly token = computed(() => this.state()?.token ?? null);

  login(request: LoginRequest): Observable<UserSession> {
    return this.http.post<LoginResponse>('/api/auth/login', request).pipe(
      map(({ token, role, fullName }) => ({ token, role, fullName })),
      tap((session) => this.store(session)),
    );
  }

  /**
   * Preview session without a token, used by the development-only role selector on the login page.
   * TODO(HU-01): remove once real login is available.
   */
  startPreviewSession(role: Role): void {
    this.store({ token: null, role, fullName: 'Usuario de prueba' });
  }

  logout(): void {
    this.state.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // Storage unavailable: the in-memory session is already cleared
    }
  }

  private store(session: UserSession): void {
    this.state.set(session);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // Storage unavailable: keep the session in memory only
    }
  }
}

function readStoredSession(): UserSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as UserSession) : null;
  } catch {
    return null;
  }
}
