import { Routes } from '@angular/router';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    title: 'Inicio | Rehab Elite',
    loadComponent: () => import('./admin-dashboard').then((m) => m.AdminDashboard),
  },
  {
    path: 'users',
    title: 'Gestión de Usuarios | Rehab Elite',
    loadComponent: () => import('./users/user-list').then((m) => m.UserList),
  },
  {
    path: 'patients',
    title: 'Registro de Pacientes | Rehab Elite',
    loadComponent: () => import('./patients/patient-registry').then((m) => m.PatientRegistry),
  },
  {
    path: 'patients/:id',
    title: 'Paciente | Rehab Elite',
    loadComponent: () => import('./patients/patient-detail').then((m) => m.PatientDetail),
  },
  {
    path: 'audit',
    title: 'Auditoría | Rehab Elite',
    loadComponent: () => import('../audit/audit-log-list').then((m) => m.AuditLogList),
  },
  // No reports: they carry clinical data, which the administrator never sees (HU-06, HU-14)
];
