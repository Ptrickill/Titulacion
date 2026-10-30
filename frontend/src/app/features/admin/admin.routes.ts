import { Routes } from '@angular/router';
import { PlaceholderPage, PlaceholderPageData } from '../../shared/layout/placeholder-page';

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
  {
    path: 'reports',
    title: 'Reportes Consolidados | Rehab Elite',
    component: PlaceholderPage,
    data: {
      heading: 'Reportes Consolidados',
      subtitle: 'Métricas de tratamientos, adherencia clínica y distribución por fisioterapeuta',
      icon: 'bar_chart',
    } satisfies PlaceholderPageData,
  },
];
