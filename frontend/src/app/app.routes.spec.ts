import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import type { MockInstance } from 'vitest';
import { routes } from './app.routes';
import { logInForTest } from './core/auth/auth-testing';

/** HU-04 on the real route table: each role only reaches its own panel. */
describe('app routes', () => {
  let harness: RouterTestingHarness;
  let router: Router;
  let snackBarOpen: MockInstance<MatSnackBar['open']>;
  const snackBarMessages = () => snackBarOpen.mock.calls.map(([message]) => message);

  beforeEach(async () => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    snackBarOpen = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    harness = await RouterTestingHarness.create();
    router = TestBed.inject(Router);
  });

  it('sends anonymous users from any panel to /login', async () => {
    await harness.navigateByUrl('/physio/patients');

    expect(router.url).toBe('/login');
  });

  it('redirects a role trying another panel back to its own panel', async () => {
    logInForTest('PHYSIOTHERAPIST');

    for (const url of ['/admin/users', '/director/reports', '/patient']) {
      await harness.navigateByUrl(url);
      expect(router.url).toBe('/physio');
    }
    expect(snackBarMessages()).toEqual([
      'No tiene acceso a esa sección.',
      'No tiene acceso a esa sección.',
      'No tiene acceso a esa sección.',
    ]);
  });

  it('lets each role into its own section', async () => {
    logInForTest('ADMINISTRATOR');

    await harness.navigateByUrl('/admin/users');

    expect(router.url).toBe('/admin/users');
    expect(snackBarMessages()).toEqual([]);
  });

  it('sends unknown URLs to the role panel', async () => {
    logInForTest('PATIENT');

    await harness.navigateByUrl('/no-existe');

    expect(router.url).toBe('/patient');
  });

  it('renders the access denied page inside the shell', async () => {
    logInForTest('OPERATIONS_DIRECTOR');

    await harness.navigateByUrl('/access-denied');

    expect(router.url).toBe('/access-denied');
    expect(harness.routeNativeElement?.textContent).toContain('Acceso denegado');
  });
});
