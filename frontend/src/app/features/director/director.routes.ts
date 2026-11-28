import { Routes } from '@angular/router';

export const DIRECTOR_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'reports' },
  {
    path: 'reports',
    title: 'Reportes de Dirección Operativa | Rehab Elite',
    loadComponent: () => import('./reports/operations-report').then((m) => m.OperationsReport),
  },
  // MEJ-05: "Gestión de Personal", the staff and its accounts, and each member's record (with the schedule)
  {
    path: 'staff',
    title: 'Gestión de Personal | Rehab Elite',
    loadComponent: () => import('./staff/staff-list').then((m) => m.StaffList),
  },
  {
    path: 'staff/:id',
    title: 'Personal | Rehab Elite',
    loadComponent: () => import('./staff/staff-detail').then((m) => m.StaffDetail),
  },
  {
    path: 'schedules',
    title: 'Horarios y asignaciones | Rehab Elite',
    loadComponent: () => import('./schedules/schedules-page').then((m) => m.SchedulesPage),
  },
  // MEJ-06: the erasure requests of the data subjects, which only the operations director accepts
  {
    path: 'data-requests',
    title: 'Solicitudes de eliminación | Rehab Elite',
    loadComponent: () => import('./data-requests/erasure-requests').then((m) => m.ErasureRequests),
  },
  {
    path: 'audit',
    title: 'Auditoría | Rehab Elite',
    loadComponent: () => import('../audit/audit-log-list').then((m) => m.AuditLogList),
  },
];
