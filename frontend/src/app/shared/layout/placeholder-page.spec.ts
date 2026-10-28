import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { PlaceholderPage, PlaceholderPageData } from './placeholder-page';

describe('PlaceholderPage', () => {
  it('renders the heading and subtitle from the route data', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'users',
            component: PlaceholderPage,
            data: {
              heading: 'Gestión de Usuarios y Roles',
              subtitle: 'Control de accesos',
              icon: 'shield_person',
            } satisfies PlaceholderPageData,
          },
        ]),
      ],
    });
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/users');
    const element = harness.routeNativeElement as HTMLElement;

    expect(TestBed.inject(Router).url).toBe('/users');
    expect(element.querySelector('h1')?.textContent).toBe('Gestión de Usuarios y Roles');
    expect(element.querySelector('.subtitle')?.textContent).toBe('Control de accesos');
    expect(element.querySelector('app-empty-state mat-icon')?.textContent).toBe('shield_person');
  });
});
