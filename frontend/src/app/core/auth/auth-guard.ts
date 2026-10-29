import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth-service';
import { Role, ROLE_HOME } from './role';
import { SectionAccessNotice } from './section-access-notice';

/** Only lets users with a valid (not expired) session through; everyone else goes to /login. */
export const authGuard: CanActivateFn = () => {
  return inject(AuthService).hasValidSession() || inject(Router).createUrlTree(['/login']);
};

/**
 * Only lets users with the given role through. Other users are sent to their own home panel, where the shell
 * shows a notice (HU-04); anonymous users go to /login.
 */
export function roleGuard(allowed: Role): CanActivateFn {
  return () => {
    const role = inject(AuthService).role();
    if (role === allowed) {
      return true;
    }
    if (!role) {
      return inject(Router).createUrlTree(['/login']);
    }
    inject(SectionAccessNotice).request();
    return inject(Router).createUrlTree([ROLE_HOME[role]]);
  };
}
