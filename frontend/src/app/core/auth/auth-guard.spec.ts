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

  it('roleGuard lets the matching role through', () => {
    logInForTest('ADMINISTRATOR');
    expect(run(roleGuard('ADMINISTRATOR'))).toBe(true);
  });

  it('roleGuard sends other roles to their own panel', () => {
    logInForTest('PATIENT');
    expect(redirectOf(run(roleGuard('ADMINISTRATOR')))).toBe('/patient');
  });

  it('roleGuard sends anonymous users to /login', () => {
    expect(redirectOf(run(roleGuard('PHYSIOTHERAPIST')))).toBe('/login');
  });
});
