import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, Routes } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { ADMIN_ROUTES } from './admin.routes';

@Component({ template: '' })
class Page {}

/** The admin routes with an empty page instead of each lazy component: only the paths and redirects matter. */
const routesWithoutPages: Routes = ADMIN_ROUTES.map((route) =>
  route.loadComponent ? { path: route.path, component: Page } : route,
);

describe('ADMIN_ROUTES (MEJ-02)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'admin', children: routesWithoutPages }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
  });

  it('serves the people registry and the records of patients and staff in English routes', () => {
    const paths = ADMIN_ROUTES.map((route) => route.path);

    expect(paths).toContain('people');
    expect(paths).toContain('people/patients/:id');
    expect(paths).toContain('people/staff/:id');
  });

  it('redirects /admin/patients to /admin/people', async () => {
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/admin/patients');

    expect(TestBed.inject(Router).url).toBe('/admin/people');
  });

  it('redirects the record of a patient to its new route', async () => {
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/admin/patients/15');

    expect(TestBed.inject(Router).url).toBe('/admin/people/patients/15');
  });

  it('serves the public /confirmar-correo page of the emailed link', () => {
    expect(routes.find((route) => route.path === 'confirmar-correo')?.canActivate).toBeUndefined();
    expect(routes.map((route) => route.path)).toContain('confirmar-correo');
  });
});
