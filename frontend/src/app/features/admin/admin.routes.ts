import { Routes } from '@angular/router';

/**
 * The reception (role ADMINISTRATOR, MEJ-05): the patients and their portal access, and its part of the audit log.
 * The staff is managed by the operations director (/director/staff).
 */
export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    title: 'Inicio | Rehab Elite',
    loadComponent: () => import('./admin-dashboard').then((m) => m.AdminDashboard),
  },
  // MEJ-05: "Registro de Pacientes", the patients only
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
  // Former links ("Gestión de Usuarios" until MEJ-02, "Registro de Usuarios" until MEJ-05) keep working; a staff
  // record is no longer the reception's, so its link goes to the patient registry
  { path: 'users', pathMatch: 'full', redirectTo: 'patients' },
  { path: 'people', pathMatch: 'full', redirectTo: 'patients' },
  { path: 'people/patients/:id', redirectTo: 'patients/:id' },
  { path: 'people/staff/:id', redirectTo: 'patients' },
  {
    path: 'audit',
    title: 'Auditoría | Rehab Elite',
    loadComponent: () => import('../audit/audit-log-list').then((m) => m.AuditLogList),
  },
  // No reports: they carry clinical data, which the reception never sees (HU-06, HU-14)
];
