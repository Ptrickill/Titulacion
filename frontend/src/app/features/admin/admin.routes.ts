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
    component: PlaceholderPage,
    data: {
      heading: 'Gestión de Usuarios y Roles',
      subtitle: 'Control de accesos, roles autorizados y seguridad de la plataforma',
      icon: 'shield_person',
    } satisfies PlaceholderPageData,
  },
  {
    path: 'patients',
    title: 'Registro de Pacientes | Rehab Elite',
    component: PlaceholderPage,
    data: {
      heading: 'Registro y Control de Pacientes',
      subtitle: 'Admisión de pacientes, datos personales y actualización de expedientes',
      icon: 'group',
    } satisfies PlaceholderPageData,
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
