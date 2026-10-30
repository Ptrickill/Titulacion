import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService, SESSION_STORAGE_KEY, UserSession } from './auth-service';
import { fakeLoginResponse } from './auth-testing';

describe('AuthService', () => {
  let http: HttpTestingController;

  function configure(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  function storedSession(expiresInMs: number): string {
    const { token, user } = fakeLoginResponse('PATIENT', 'Rosa Ortiz');
    const session: UserSession = {
      token,
      user,
      expiresAt: new Date(Date.now() + expiresInMs).toISOString(),
    };
    return JSON.stringify(session);
  }

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('starts without a session', () => {
    const auth = configure();

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.role()).toBeNull();
    expect(auth.user()).toBeNull();
  });

  it('posts the credentials and keeps the session in sessionStorage by default', () => {
    const auth = configure();
    let loggedIn: unknown;

    auth
      .login({ email: 'fisio@rehabelite.test', password: 'secret' })
      .subscribe((user) => (loggedIn = user));
    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'fisio@rehabelite.test', password: 'secret' });
    request.flush(fakeLoginResponse('PHYSIOTHERAPIST', 'Ana Pérez'));

    expect(loggedIn).toEqual(
      expect.objectContaining({ fullName: 'Ana Pérez', role: 'PHYSIOTHERAPIST' }),
    );
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.role()).toBe('PHYSIOTHERAPIST');
    expect(auth.token()).toBe('jwt-token');
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toContain('jwt-token');
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('keeps the session in localStorage when "remember me" is checked', () => {
    const auth = configure();

    auth.login({ email: 'fisio@rehabelite.test', password: 'secret' }, true).subscribe();
    http.expectOne('/api/auth/login').flush(fakeLoginResponse('ADMINISTRATOR'));

    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toContain('jwt-token');
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('does not store anything when the login fails', () => {
    const auth = configure();

    auth
      .login({ email: 'fisio@rehabelite.test', password: 'wrong' })
      .subscribe({ error: () => undefined });
    http.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('restores a stored session that has not expired', () => {
    localStorage.setItem(SESSION_STORAGE_KEY, storedSession(10 * 60_000));

    const auth = configure();

    expect(auth.user()?.fullName).toBe('Rosa Ortiz');
    expect(auth.hasValidSession()).toBe(true);
  });

  it('discards an expired or corrupt stored session', () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, storedSession(-1000));
    localStorage.setItem(SESSION_STORAGE_KEY, '{not json');

    const auth = configure();

    expect(auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('ends the session and goes to /login when the token expires', async () => {
    vi.useFakeTimers();
    const auth = configure();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.login({ email: 'fisio@rehabelite.test', password: 'secret' }).subscribe();
    http.expectOne('/api/auth/login').flush(fakeLoginResponse('PHYSIOTHERAPIST'));
    TestBed.tick();

    await vi.advanceTimersByTimeAsync(30 * 60_000);

    expect(auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('clears the session from both storages on logout', () => {
    const auth = configure();
    auth.login({ email: 'fisio@rehabelite.test', password: 'secret' }, true).subscribe();
    http.expectOne('/api/auth/login').flush(fakeLoginResponse('ADMINISTRATOR'));

    auth.logout();

    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });
});
