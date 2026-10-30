import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { authGuard, roleGuard } from './auth-guard';
import { AuthService } from './auth-service';
import { logInForTest } from './auth-testing';
import { Role, ROLES } from './role';
import { SectionAccessNotice } from './section-access-notice';

describe('auth guards', () => {
  const run = (guard: CanActivateFn) =>
    TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  const redirectOf = (result: unknown) => (result as UrlTree).toString();

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => vi.useRealTimers());

  it('authGuard redirects anonymous users to /login', () => {
    expect(redirectOf(run(authGuard))).toBe('/login');
  });

  it('authGuard lets authenticated users through', () => {
    logInForTest('PATIENT');
    expect(run(authGuard)).toBe(true);
  });

  it('authGuard ends an expired session and redirects to /login', () => {
    logInForTest('PATIENT');
    vi.useFakeTimers({ now: Date.now() + 31 * 60_000, toFake: ['Date'] });

    expect(redirectOf(run(authGuard))).toBe('/login');
    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false);
  });

  it('roleGuard lets the matching role through without a notice', () => {
    const notice = vi.spyOn(TestBed.inject(SectionAccessNotice), 'request');
    logInForTest('ADMINISTRATOR');

    expect(run(roleGuard('ADMINISTRATOR'))).toBe(true);
    expect(notice).not.toHaveBeenCalled();
  });

  it('roleGuard sends other roles to their own panel with a notice', () => {
    const notice = vi.spyOn(TestBed.inject(SectionAccessNotice), 'request');
    const panels = {
      ADMINISTRATOR: '/admin',
      PHYSIOTHERAPIST: '/physio',
      OPERATIONS_DIRECTOR: '/director',
      PATIENT: '/patient',
    } as const;

    for (const [role, panel] of Object.entries(panels) as [Role, string][]) {
      TestBed.inject(AuthService).logout();
      logInForTest(role);
      const forbidden = ROLES.filter((other) => other !== role);
      for (const other of forbidden) {
        expect(redirectOf(run(roleGuard(other)))).toBe(panel);
      }
    }
    expect(notice).toHaveBeenCalledTimes(12);
  });

  it('roleGuard sends anonymous users to /login without a notice', () => {
    const notice = vi.spyOn(TestBed.inject(SectionAccessNotice), 'request');

    expect(redirectOf(run(roleGuard('PHYSIOTHERAPIST')))).toBe('/login');
    expect(notice).not.toHaveBeenCalled();
  });
});
