import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Login } from './login';

/** MEJ-06: after a close by inactivity the login page says why. */
describe('Login after a close by inactivity', () => {
  it('tells that the session was closed for inactivity', async () => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ reason: 'idle' }) } },
        },
      ],
    });
    const fixture = TestBed.createComponent(Login);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('.info')?.textContent).toContain(
      'Su sesión se cerró por inactividad. Ingrese de nuevo.',
    );
  });
});
