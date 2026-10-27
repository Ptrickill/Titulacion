import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthSession } from './auth-session';

const LOGIN_URL = '/api/auth/login';

/**
 * Adds the bearer token to API calls and ends the session when the backend answers 401.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(AuthSession);
  const router = inject(Router);
  const token = session.token();

  const authorized =
    token && request.url.startsWith('/api/')
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request;

  return next(authorized).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && request.url !== LOGIN_URL) {
        session.logout();
        void router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
