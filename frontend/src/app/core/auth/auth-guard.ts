import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSession } from './auth-session';
import { Role, ROLE_HOME } from './role';

/** Only lets authenticated users through; everyone else goes to /login. */
export const authGuard: CanActivateFn = () => {
  return inject(AuthSession).isAuthenticated() || inject(Router).createUrlTree(['/login']);
};

/** Only lets users with the given role through; others are sent to their own home panel. */
export function roleGuard(allowed: Role): CanActivateFn {
  return () => {
    const role = inject(AuthSession).role();
    if (role === allowed) {
      return true;
    }
    return inject(Router).createUrlTree([role ? ROLE_HOME[role] : '/login']);
  };
}
