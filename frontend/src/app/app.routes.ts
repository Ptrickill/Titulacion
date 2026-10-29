import { inject, isDevMode } from '@angular/core';
import { RedirectFunction, Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth/auth-guard';
import { AuthService } from './core/auth/auth-service';
import { ROLE_HOME } from './core/auth/role';

/** Home panel of the logged-in user's role, or /login without a valid session. */
const toRoleHome: RedirectFunction = () => {
  const auth = inject(AuthService);
  return auth.hasValidSession() ? ROLE_HOME[auth.role()!] : '/login';
};

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: toRoleHome },
  {
    path: 'login',
    title: 'Iniciar sesión | Rehab Elite',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: '',
    loadComponent: () => import('./shared/layout/app-shell').then((m) => m.AppShell),
    canActivate: [authGuard],
    children: [
      {
        path: 'physio',
        canActivate: [roleGuard('PHYSIOTHERAPIST')],
        loadChildren: () =>
          import('./features/physiotherapist/physio.routes').then((m) => m.PHYSIO_ROUTES),
      },
      {
        path: 'admin',
        canActivate: [roleGuard('ADMINISTRATOR')],
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
      },
      {
        path: 'director',
        canActivate: [roleGuard('OPERATIONS_DIRECTOR')],
        loadChildren: () =>
          import('./features/director/director.routes').then((m) => m.DIRECTOR_ROUTES),
      },
      {
        path: 'patient',
        canActivate: [roleGuard('PATIENT')],
        loadChildren: () =>
          import('./features/patient/patient.routes').then((m) => m.PATIENT_ROUTES),
      },
      {
        // Shown when the API answers 403 (see authInterceptor)
        path: 'access-denied',
        title: 'Acceso denegado | Rehab Elite',
        loadComponent: () =>
          import('./shared/layout/access-denied-page').then((m) => m.AccessDeniedPage),
      },
      {
        // Development-only component gallery; not matched in production builds
        path: 'ui-kit',
        title: 'Catálogo de componentes | Rehab Elite',
        canMatch: [() => isDevMode()],
        loadComponent: () => import('./features/dev/ui-kit').then((m) => m.UiKit),
      },
    ],
  },
  // Unknown URLs go to the user's own panel (or to /login without a session)
  { path: '**', redirectTo: toRoleHome },
];
