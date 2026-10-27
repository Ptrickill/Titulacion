import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authInterceptor } from './auth-interceptor';
import { AuthSession } from './auth-session';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let session: AuthSession;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(AuthSession);
  });

  afterEach(() => backend.verify());

  function logIn(): void {
    session.login({ email: 'admin@rehabelite.test', password: 'secret' }).subscribe();
    backend
      .expectOne('/api/auth/login')
      .flush({ token: 'jwt-token', role: 'ADMINISTRATOR', fullName: 'Admin' });
  }

  it('adds the bearer token to API requests', () => {
    logIn();

    http.get('/api/patients').subscribe();

    expect(backend.expectOne('/api/patients').request.headers.get('Authorization')).toBe(
      'Bearer jwt-token',
    );
  });

  it('does not send a token when there is no session', () => {
    http.get('/api/patients').subscribe();

    expect(backend.expectOne('/api/patients').request.headers.has('Authorization')).toBe(false);
  });

  it('ends the session and goes to /login on 401', () => {
    logIn();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    http.get('/api/patients').subscribe({ error: () => undefined });
    backend.expectOne('/api/patients').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(session.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
