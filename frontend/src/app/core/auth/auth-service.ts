import { HttpClient } from '@angular/common/http';
import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { map, Observable, tap } from 'rxjs';
import { Role, ROLES } from './role';

export interface LoginRequest {
  email: string;
  password: string;
}

/** Authenticated user, as returned by the backend (same data as the JWT claims). */
export interface AuthUser {
  id: number;
  email: string;
  fullName: string;
  role: Role;
}

/** Response of POST /api/auth/login. */
export interface LoginResponse {
  token: string;
  tokenType: string;
  /** ISO-8601 instant (UTC). */
  expiresAt: string;
  user: AuthUser;
}

export interface UserSession {
  token: string;
  expiresAt: string;
  user: AuthUser;
}

export const LOGIN_URL = '/api/auth/login';
export const SESSION_STORAGE_KEY = 'rehab-elite.session';

/** setTimeout cannot wait longer than this (about 24.8 days). */
const MAX_TIMEOUT_MS = 2_147_483_647;

/**
 * Logs the user in and holds the session (token and user) in signals.
 * "Remember me" keeps the session in localStorage; otherwise it lives in sessionStorage and ends with the tab.
 * The session ends automatically when the token expires.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly state = signal<UserSession | null>(readStoredSession());

  readonly session = this.state.asReadonly();
  readonly isAuthenticated = computed(() => this.state() !== null);
  readonly user = computed(() => this.state()?.user ?? null);
  readonly role = computed(() => this.state()?.user.role ?? null);
  readonly token = computed(() => this.state()?.token ?? null);

  constructor() {
    effect((onCleanup) => {
      const session = this.state();
      if (!session) {
        return;
      }
      const remaining = Date.parse(session.expiresAt) - Date.now();
      const timeout = setTimeout(
        () => this.expire(),
        Math.min(Math.max(remaining, 0), MAX_TIMEOUT_MS),
      );
      onCleanup(() => clearTimeout(timeout));
    });
  }

  login(request: LoginRequest, remember = false): Observable<AuthUser> {
    return this.http.post<LoginResponse>(LOGIN_URL, request).pipe(
      map(({ token, expiresAt, user }) => ({ token, expiresAt, user })),
      tap((session) => this.store(session, remember)),
      map((session) => session.user),
    );
  }

  /** True while the session exists and its token has not expired; an expired session is cleared. */
  hasValidSession(): boolean {
    const session = this.state();
    if (session && isExpired(session)) {
      this.logout();
      return false;
    }
    return session !== null;
  }

  logout(): void {
    this.state.set(null);
    clearStoredSession();
  }

  private expire(): void {
    this.logout();
    void this.router.navigate(['/login']);
  }

  private store(session: UserSession, remember: boolean): void {
    this.state.set(session);
    clearStoredSession();
    try {
      (remember ? localStorage : sessionStorage).setItem(
        SESSION_STORAGE_KEY,
        JSON.stringify(session),
      );
    } catch {
      // Storage unavailable: keep the session in memory only
    }
  }
}

function isExpired(session: UserSession): boolean {
  return !(Date.parse(session.expiresAt) > Date.now());
}

function readStoredSession(): UserSession | null {
  for (const storage of [sessionStorage, localStorage]) {
    try {
      const raw = storage.getItem(SESSION_STORAGE_KEY);
      const session = raw ? (JSON.parse(raw) as Partial<UserSession>) : null;
      if (isUsableSession(session)) {
        return session;
      }
      if (raw) {
        storage.removeItem(SESSION_STORAGE_KEY);
      }
    } catch {
      // Unreadable or corrupt entry: ignore it
    }
  }
  return null;
}

function isUsableSession(session: Partial<UserSession> | null): session is UserSession {
  return (
    typeof session?.token === 'string' &&
    typeof session.expiresAt === 'string' &&
    ROLES.includes(session.user?.role as Role) &&
    !isExpired(session as UserSession)
  );
}

function clearStoredSession(): void {
  for (const storage of [sessionStorage, localStorage]) {
    try {
      storage.removeItem(SESSION_STORAGE_KEY);
    } catch {
      // Storage unavailable: nothing to clear
    }
  }
}
