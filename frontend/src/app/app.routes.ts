import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth/auth-guard';
import { AuthSession } from './core/auth/auth-session';
import { ROLE_HOME } from './core/auth/role';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: () => {
      const role = inject(AuthSession).role();
      return role ? ROLE_HOME[role] : '/login';
    },
  },
  {
    path: 'login',
    title: 'Iniciar sesión | Rehab Elite',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: '',
    loadComponent: () => import('./shared/layout/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    children: [
      {
        path: 'admin',
        title: 'Panel de administración',
        canActivate: [roleGuard('ADMINISTRATOR')],
        loadComponent: () =>
          import('./features/admin/admin-dashboard').then((m) => m.AdminDashboard),
      },
      {
        path: 'physio',
        title: 'Panel del fisioterapeuta',
        canActivate: [roleGuard('PHYSIOTHERAPIST')],
        loadComponent: () =>
          import('./features/physiotherapist/physio-dashboard').then((m) => m.PhysioDashboard),
      },
      {
        path: 'director',
        title: 'Panel de dirección operativa',
        canActivate: [roleGuard('OPERATIONS_DIRECTOR')],
        loadComponent: () =>
          import('./features/director/director-dashboard').then((m) => m.DirectorDashboard),
      },
      {
        path: 'patient',
        title: 'Mi portal',
        canActivate: [roleGuard('PATIENT')],
        loadComponent: () =>
          import('./features/patient/patient-dashboard').then((m) => m.PatientDashboard),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
