import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authInterceptor } from './auth-interceptor';
import { AuthService } from './auth-service';
import { logInForTest } from './auth-testing';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => backend.verify());

  it('adds the bearer token to API requests', () => {
    logInForTest('ADMINISTRATOR');

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
    logInForTest('ADMINISTRATOR');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    http.get('/api/patients').subscribe({ error: () => undefined });
    backend.expectOne('/api/patients').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('keeps the session and shows the access denied page on 403', () => {
    logInForTest('PATIENT');
    const navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    let failed = false;

    http.get('/api/clinical-records/7').subscribe({ error: () => (failed = true) });
    backend
      .expectOne('/api/clinical-records/7')
      .flush({ title: 'Acceso denegado', status: 403 }, { status: 403, statusText: 'Forbidden' });

    expect(failed).toBe(true);
    expect(auth.isAuthenticated()).toBe(true);
    expect(navigateByUrl).toHaveBeenCalledWith('/access-denied');
  });

  it('leaves a failed login to the login page', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    auth
      .login({ email: 'admin@rehabelite.test', password: 'wrong' })
      .subscribe({ error: () => undefined });
    backend.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(navigate).not.toHaveBeenCalled();
  });
});
