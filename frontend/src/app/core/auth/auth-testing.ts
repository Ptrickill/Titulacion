import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthUser, AuthService, LOGIN_URL, LoginResponse } from './auth-service';
import { Role } from './role';

/** Synthetic login response for unit tests (token valid for 30 minutes). */
export function fakeLoginResponse(role: Role, fullName = 'Usuario de prueba'): LoginResponse {
  const user: AuthUser = { id: 1, email: 'usuario@rehabelite.test', fullName, role };
  return {
    token: 'jwt-token',
    tokenType: 'Bearer',
    expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
    user,
  };
}

/**
 * Logs in through the real AuthService, answering the HTTP call with a synthetic response.
 * Requires provideHttpClient() and provideHttpClientTesting() in the test module.
 */
export function logInForTest(role: Role, fullName?: string): void {
  TestBed.inject(AuthService)
    .login({ email: 'usuario@rehabelite.test', password: 'secret' })
    .subscribe();
  TestBed.inject(HttpTestingController)
    .expectOne(LOGIN_URL)
    .flush(fakeLoginResponse(role, fullName));
}
