import { Routes } from '@angular/router';

export const DIRECTOR_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'reports' },
  {
    path: 'reports',
    title: 'Reportes de Dirección Operativa | Rehab Elite',
    loadComponent: () => import('./reports/operations-report').then((m) => m.OperationsReport),
  },
  {
    path: 'audit',
    title: 'Auditoría | Rehab Elite',
    loadComponent: () => import('../audit/audit-log-list').then((m) => m.AuditLogList),
  },
];
