import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth-service';

/** localStorage key of the last activity, shared by the tabs of a remembered session. */
export const LAST_ACTIVITY_KEY = 'rehab-elite.last-activity';
/** Query parameter of /login after a close by inactivity. */
export const IDLE_LOGOUT_REASON = 'idle';
/** Events that count as activity of the user. */
const ACTIVITY_EVENTS = [
  'pointerdown',
  'pointermove',
  'keydown',
  'wheel',
  'touchstart',
  'scroll',
] as const;
/** Activity is recorded at most this often: pointermove fires many times per second. */
const ACTIVITY_THROTTLE_MS = 5_000;
const TICK_MS = 1_000;

/**
 * Closes the session after a time without activity (MEJ-06, app.session.idle-timeout, 15 minutes by default), warning
 * before (idle-warning, 2 minutes). The time comes with the login. Activity is any click, key, scroll or pointer
 * movement; while the warning is shown only "Seguir conectado" keeps the session. A remembered session shares its
 * last activity between tabs through localStorage, so working in one tab keeps the others open.
 *
 * The server does not track inactivity (the JWT keeps no session): the expiration of the token is still the hard
 * limit.
 */
@Injectable({ providedIn: 'root' })
export class IdleSession {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  private lastActivity = Date.now();
  private lastRecorded = 0;
  private stop: (() => void) | null = null;

  private readonly warningState = signal(false);
  private readonly secondsLeftState = signal(0);

  /** True while the warning before the close is shown. */
  readonly warning = this.warningState.asReadonly();
  /** Seconds until the close, while the warning is shown. */
  readonly secondsLeft = this.secondsLeftState.asReadonly();

  /** Starts watching the activity until {@link end} or until the caller is destroyed. */
  start(destroyRef?: DestroyRef): void {
    if (this.stop) {
      return;
    }
    this.markActivity(true);
    const onActivity = () => {
      if (!this.warningState()) {
        this.markActivity(false);
      }
    };
    for (const event of ACTIVITY_EVENTS) {
      document.addEventListener(event, onActivity, { passive: true, capture: true });
    }
    const timer = setInterval(() => this.tick(), TICK_MS);
    this.stop = () => {
      clearInterval(timer);
      for (const event of ACTIVITY_EVENTS) {
        document.removeEventListener(event, onActivity, { capture: true });
      }
    };
    destroyRef?.onDestroy(() => this.end());
  }

  /** Stops watching (logout or the layout is destroyed). */
  end(): void {
    this.stop?.();
    this.stop = null;
    this.warningState.set(false);
  }

  /** "Seguir conectado": the warning closes and the time starts again. */
  stayConnected(): void {
    this.markActivity(true);
    this.warningState.set(false);
  }

  private tick(): void {
    if (!this.auth.isAuthenticated()) {
      this.end();
      return;
    }
    const remaining = this.lastActivityAcrossTabs() + this.auth.idleTimeoutMs() - Date.now();
    if (remaining <= 0) {
      this.end();
      this.auth.logout();
      void this.router.navigate(['/login'], { queryParams: { reason: IDLE_LOGOUT_REASON } });
      return;
    }
    const warn = remaining <= this.auth.idleWarningMs();
    this.warningState.set(warn);
    this.secondsLeftState.set(Math.ceil(remaining / 1000));
  }

  private markActivity(force: boolean): void {
    const now = Date.now();
    if (!force && now - this.lastRecorded < ACTIVITY_THROTTLE_MS) {
      return;
    }
    this.lastRecorded = now;
    this.lastActivity = now;
    if (this.auth.remembered()) {
      try {
        localStorage.setItem(LAST_ACTIVITY_KEY, String(now));
      } catch {
        // Storage unavailable: this tab keeps its own time
      }
    }
  }

  private lastActivityAcrossTabs(): number {
    if (!this.auth.remembered()) {
      return this.lastActivity;
    }
    try {
      const shared = Number(localStorage.getItem(LAST_ACTIVITY_KEY));
      return Number.isFinite(shared) && shared > this.lastActivity ? shared : this.lastActivity;
    } catch {
      return this.lastActivity;
    }
  }
}
