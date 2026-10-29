import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { logInForTest } from '../../core/auth/auth-testing';
import { AccessDeniedPage } from './access-denied-page';

describe('AccessDeniedPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('explains the denial in Spanish and links to the role panel', async () => {
    logInForTest('PHYSIOTHERAPIST');
    const fixture = TestBed.createComponent(AccessDeniedPage);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('Acceso denegado');
    expect(element.textContent).toContain('No tiene permisos para esta acción');
    const link = element.querySelector<HTMLAnchorElement>('a')!;
    expect(link.textContent).toContain('Ir a mi panel');
    expect(link.getAttribute('href')).toBe('/physio');
  });
});
