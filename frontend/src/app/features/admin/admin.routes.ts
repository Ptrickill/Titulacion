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
  // MEJ-02: "Registro de Usuarios", patients and staff in one registry
  {
    path: 'people',
    title: 'Registro de Usuarios | Rehab Elite',
    loadComponent: () => import('./people/people-registry').then((m) => m.PeopleRegistry),
  },
  {
    path: 'people/patients/:id',
    title: 'Paciente | Rehab Elite',
    loadComponent: () => import('./patients/patient-detail').then((m) => m.PatientDetail),
  },
  {
    path: 'people/staff/:id',
    title: 'Personal | Rehab Elite',
    loadComponent: () => import('./people/staff-detail').then((m) => m.StaffDetail),
  },
  // The registry lived at /admin/patients until MEJ-02: old links and bookmarks keep working
  { path: 'patients', pathMatch: 'full', redirectTo: 'people' },
  { path: 'patients/:id', redirectTo: 'people/patients/:id' },
  {
    path: 'audit',
    title: 'Auditoría | Rehab Elite',
    loadComponent: () => import('../audit/audit-log-list').then((m) => m.AuditLogList),
  },
  // No reports: they carry clinical data, which the administrator never sees (HU-06, HU-14)
];
