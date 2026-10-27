import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthSession } from './auth-session';

describe('AuthSession', () => {
  let session: AuthSession;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    session = TestBed.inject(AuthSession);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts without a session', () => {
    expect(session.isAuthenticated()).toBe(false);
    expect(session.role()).toBeNull();
  });

  it('posts credentials to the login endpoint and stores the session', () => {
    session.login({ email: 'fisio@rehabelite.test', password: 'secret' }).subscribe();

    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'fisio@rehabelite.test', password: 'secret' });
    request.flush({ token: 'jwt-token', role: 'PHYSIOTHERAPIST', fullName: 'Ana Pérez' });

    expect(session.isAuthenticated()).toBe(true);
    expect(session.role()).toBe('PHYSIOTHERAPIST');
    expect(session.token()).toBe('jwt-token');
    expect(sessionStorage.getItem('rehab-elite.session')).toContain('jwt-token');
  });

  it('clears the session on logout', () => {
    session.startPreviewSession('ADMINISTRATOR');

    session.logout();

    expect(session.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem('rehab-elite.session')).toBeNull();
  });
});
