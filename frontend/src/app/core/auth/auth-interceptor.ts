import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService, LOGIN_URL } from './auth-service';

export const ACCESS_DENIED_ROUTE = '/access-denied';

/**
 * Adds the bearer token to API calls and reacts to security errors:
 * - 401 (missing, invalid or expired token): ends the session and goes to /login. A 401 from the login
 *   itself is a wrong password, not an expired session.
 * - 403 (role not allowed, HU-04): keeps the session and shows the access denied page.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();

  const authorized =
    token && request.url.startsWith('/api/') && request.url !== LOGIN_URL
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request;

  return next(authorized).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && request.url !== LOGIN_URL) {
        if (error.status === 401) {
          auth.logout();
          void router.navigate(['/login']);
        } else if (error.status === 403) {
          void router.navigateByUrl(ACCESS_DENIED_ROUTE);
        }
      }
      return throwError(() => error);
    }),
  );
};
