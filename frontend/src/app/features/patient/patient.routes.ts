import { Routes } from '@angular/router';

export const PATIENT_ROUTES: Routes = [
  {
    path: '',
    title: 'Mi Tratamiento | Rehab Elite',
    loadComponent: () => import('./patient-dashboard').then((m) => m.PatientDashboard),
  },
];
