import { Routes } from '@angular/router';
import { PlaceholderPage, PlaceholderPageData } from '../../shared/layout/placeholder-page';

export const DIRECTOR_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'reports' },
  {
    path: 'reports',
    title: 'Reportes de Dirección Operativa | Rehab Elite',
    component: PlaceholderPage,
    data: {
      heading: 'Reportes de Dirección Operativa',
      subtitle: 'Métricas de tratamientos, adherencia clínica y distribución por fisioterapeuta',
      icon: 'bar_chart',
    } satisfies PlaceholderPageData,
  },
  {
    path: 'audit',
    title: 'Auditoría | Rehab Elite',
    loadComponent: () => import('../audit/audit-log-list').then((m) => m.AuditLogList),
  },
];
