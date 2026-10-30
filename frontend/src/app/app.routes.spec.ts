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

  it('opens the audit log for the administrator and the operations director (HU-18)', async () => {
    logInForTest('ADMINISTRATOR');
    await harness.navigateByUrl('/admin/audit');
    expect(router.url).toBe('/admin/audit');
    expect(harness.routeNativeElement?.querySelector('app-audit-log-list')).not.toBeNull();

    logInForTest('OPERATIONS_DIRECTOR');
    await harness.navigateByUrl('/director/audit');
    expect(router.url).toBe('/director/audit');
    expect(harness.routeNativeElement?.querySelector('app-audit-log-list')).not.toBeNull();
    expect(snackBarMessages()).toEqual([]);
  });

  it('keeps the other roles out of the audit log', async () => {
    for (const role of ['PHYSIOTHERAPIST', 'PATIENT'] as const) {
      logInForTest(role);
      for (const url of ['/admin/audit', '/director/audit']) {
        await harness.navigateByUrl(url);
        expect(router.url).not.toContain('/audit');
      }
    }
    expect(snackBarMessages()).toHaveLength(4);
  });

  it('sends the administrator to the audit page of its own panel', async () => {
    logInForTest('ADMINISTRATOR');

    await harness.navigateByUrl('/director/audit');

    expect(router.url).toBe('/admin');
    expect(snackBarMessages()).toEqual(['No tiene acceso a esa sección.']);
  });

  it('sends the operations director to the audit page of its own panel', async () => {
    logInForTest('OPERATIONS_DIRECTOR');

    await harness.navigateByUrl('/admin/audit');

    expect(router.url).toBe('/director/reports');
    expect(snackBarMessages()).toEqual(['No tiene acceso a esa sección.']);
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
