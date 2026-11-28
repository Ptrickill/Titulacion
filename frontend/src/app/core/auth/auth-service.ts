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
  /** MEJ-06: the session closes after this many seconds without activity (app.session.idle-timeout). */
  idleTimeoutSeconds: number;
  /** MEJ-06: seconds before that close in which the user is warned (app.session.idle-warning). */
  idleWarningSeconds: number;
}

export interface UserSession {
  token: string;
  expiresAt: string;
  user: AuthUser;
  /** Missing in a session stored before MEJ-06: the defaults apply. */
  idleTimeoutSeconds?: number;
  idleWarningSeconds?: number;
}

export const LOGIN_URL = '/api/auth/login';
export const SESSION_STORAGE_KEY = 'rehab-elite.session';
/** MEJ-06: the defaults of the backend (15 minutes, warning 2 minutes before). */
export const DEFAULT_IDLE_TIMEOUT_SECONDS = 900;
export const DEFAULT_IDLE_WARNING_SECONDS = 120;

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
  /** MEJ-06: inactivity limit and warning of the current session, in milliseconds. */
  readonly idleTimeoutMs = computed(
    () => (this.state()?.idleTimeoutSeconds ?? DEFAULT_IDLE_TIMEOUT_SECONDS) * 1000,
  );
  readonly idleWarningMs = computed(
    () => (this.state()?.idleWarningSeconds ?? DEFAULT_IDLE_WARNING_SECONDS) * 1000,
  );
  /** Whether the session lives in localStorage ("Recordarme"), shared by the tabs of the browser. */
  readonly remembered = computed(() => this.state() !== null && readsFrom(localStorage));

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
      map(({ token, expiresAt, user, idleTimeoutSeconds, idleWarningSeconds }) => ({
        token,
        expiresAt,
        user,
        idleTimeoutSeconds,
        idleWarningSeconds,
      })),
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

function readsFrom(storage: Storage): boolean {
  try {
    return storage.getItem(SESSION_STORAGE_KEY) !== null;
  } catch {
    return false;
  }
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
