import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService, LOGIN_URL } from './auth-service';
import { fakeLoginResponse, logInForTest } from './auth-testing';
import { IdleSession, LAST_ACTIVITY_KEY } from './idle-session';

/** MEJ-06: 15 minutes without activity close the session, warning 2 minutes before (the login brings both). */
describe('IdleSession', () => {
  let idle: IdleSession;
  let auth: AuthService;
  let navigate: ReturnType<typeof vi.spyOn>;

  const minutes = (n: number) => n * 60_000;
  const activity = () => document.dispatchEvent(new KeyboardEvent('keydown'));

  beforeEach(() => {
    vi.useFakeTimers();
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    logInForTest('ADMINISTRATOR');
    auth = TestBed.inject(AuthService);
    idle = TestBed.inject(IdleSession);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    idle.start();
  });

  afterEach(() => {
    idle.end();
    vi.useRealTimers();
  });

  it('warns two minutes before the close and counts down', () => {
    vi.advanceTimersByTime(minutes(12) + 59_000);
    expect(idle.warning()).toBe(false);

    vi.advanceTimersByTime(2_000);
    expect(idle.warning()).toBe(true);
    expect(idle.secondsLeft()).toBeLessThanOrEqual(120);
    expect(idle.secondsLeft()).toBeGreaterThan(115);
  });

  it('closes the session after 15 minutes without activity and says why on /login', () => {
    vi.advanceTimersByTime(minutes(15) + 1_000);

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'idle' } });
  });

  it('any activity before the warning starts the time again', () => {
    vi.advanceTimersByTime(minutes(10));
    activity();
    vi.advanceTimersByTime(minutes(10));

    expect(auth.isAuthenticated()).toBe(true);
    expect(idle.warning()).toBe(false);
  });

  it('during the warning only "Seguir conectado" keeps the session', () => {
    vi.advanceTimersByTime(minutes(14));
    expect(idle.warning()).toBe(true);
    activity();
    vi.advanceTimersByTime(1_000);
    expect(idle.warning()).toBe(true);

    idle.stayConnected();
    vi.advanceTimersByTime(minutes(5));
    expect(idle.warning()).toBe(false);
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('a remembered session shares its last activity with the other tabs', () => {
    idle.end();
    auth.logout();
    auth.login({ email: 'usuario@rehabelite.test', password: 'secret' }, true).subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne(LOGIN_URL)
      .flush(fakeLoginResponse('ADMINISTRATOR'));
    idle.start();

    vi.advanceTimersByTime(minutes(14));
    // Another tab worked a minute ago
    localStorage.setItem(LAST_ACTIVITY_KEY, String(Date.now() - 60_000));
    vi.advanceTimersByTime(1_000);

    expect(idle.warning()).toBe(false);
    expect(auth.isAuthenticated()).toBe(true);
  });
});
