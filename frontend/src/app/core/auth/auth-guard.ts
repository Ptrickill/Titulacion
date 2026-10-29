import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth-service';
import { Role, ROLE_HOME } from './role';

/** Only lets users with a valid (not expired) session through; everyone else goes to /login. */
export const authGuard: CanActivateFn = () => {
  return inject(AuthService).hasValidSession() || inject(Router).createUrlTree(['/login']);
};

/** Only lets users with the given role through; others are sent to their own home panel. */
export function roleGuard(allowed: Role): CanActivateFn {
  return () => {
    const role = inject(AuthService).role();
    if (role === allowed) {
      return true;
    }
    return inject(Router).createUrlTree([role ? ROLE_HOME[role] : '/login']);
  };
}
