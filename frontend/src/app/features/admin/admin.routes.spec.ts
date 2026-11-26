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

describe('ADMIN_ROUTES (MEJ-05: the reception)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'admin', children: routesWithoutPages }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
  });

  it('serves the patient registry and the patient records, and no staff record', () => {
    const pages = ADMIN_ROUTES.filter((route) => route.loadComponent).map((route) => route.path);

    expect(pages).toEqual(['', 'patients', 'patients/:id', 'audit']);
  });

  it('redirects the former registries to the patient registry', async () => {
    const harness = await RouterTestingHarness.create();
    for (const url of ['/admin/people', '/admin/users', '/admin/people/staff/4']) {
      await harness.navigateByUrl(url);
      expect(TestBed.inject(Router).url).toBe('/admin/patients');
    }
  });

  it('redirects the former record of a patient to its route', async () => {
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/admin/people/patients/15');

    expect(TestBed.inject(Router).url).toBe('/admin/patients/15');
  });

  it('serves the public /confirmar-correo page of the emailed link', () => {
    expect(routes.find((route) => route.path === 'confirmar-correo')?.canActivate).toBeUndefined();
    expect(routes.map((route) => route.path)).toContain('confirmar-correo');
  });
});
