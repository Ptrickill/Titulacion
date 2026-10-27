import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { authGuard, roleGuard } from './auth-guard';
import { AuthSession } from './auth-session';

describe('auth guards', () => {
  let session: AuthSession;

  const run = (guard: CanActivateFn) =>
    TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  const redirectOf = (result: unknown) => (result as UrlTree).toString();

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient()] });
    session = TestBed.inject(AuthSession);
  });

  it('authGuard redirects anonymous users to /login', () => {
    expect(redirectOf(run(authGuard))).toBe('/login');
  });

  it('authGuard lets authenticated users through', () => {
    session.startPreviewSession('PATIENT');
    expect(run(authGuard)).toBe(true);
  });

  it('roleGuard lets the matching role through', () => {
    session.startPreviewSession('ADMINISTRATOR');
    expect(run(roleGuard('ADMINISTRATOR'))).toBe(true);
  });

  it('roleGuard sends other roles to their own panel', () => {
    session.startPreviewSession('PATIENT');
    expect(redirectOf(run(roleGuard('ADMINISTRATOR')))).toBe('/patient');
  });

  it('roleGuard sends anonymous users to /login', () => {
    expect(redirectOf(run(roleGuard('PHYSIOTHERAPIST')))).toBe('/login');
  });
});
