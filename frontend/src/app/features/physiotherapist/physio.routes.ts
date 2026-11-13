import { Routes } from '@angular/router';
import { PlaceholderPage, PlaceholderPageData } from '../../shared/layout/placeholder-page';
import type { ClinicalRecordFormData } from './clinical-records/clinical-record-form';

export const PHYSIO_ROUTES: Routes = [
  {
    path: '',
    title: 'Inicio | Rehab Elite',
    loadComponent: () => import('./physio-dashboard').then((m) => m.PhysioDashboard),
  },
  {
    path: 'patients',
    title: 'Gestión de Pacientes | Rehab Elite',
    loadComponent: () => import('./patients/physio-patient-list').then((m) => m.PhysioPatientList),
  },
  {
    path: 'patients/:patientId/clinical-record',
    title: 'Historial Clínico | Rehab Elite',
    loadComponent: () =>
      import('./clinical-records/clinical-record-view').then((m) => m.ClinicalRecordView),
  },
  {
    path: 'patients/:patientId/clinical-record/new',
    title: 'Nuevo Historial Clínico | Rehab Elite',
    loadComponent: () =>
      import('./clinical-records/clinical-record-form').then((m) => m.ClinicalRecordForm),
  },
  {
    path: 'patients/:patientId/clinical-record/edit',
    title: 'Editar Historial Clínico | Rehab Elite',
    data: { mode: 'edit' } satisfies ClinicalRecordFormData,
    loadComponent: () =>
      import('./clinical-records/clinical-record-form').then((m) => m.ClinicalRecordForm),
  },
  {
    path: 'patients/:patientId/clinical-record/:recordId/treatments/new',
    title: 'Nuevo Tratamiento | Rehab Elite',
    loadComponent: () =>
      import('./clinical-records/clinical-record-form').then((m) => m.ClinicalRecordForm),
  },
  {
    path: 'patients/:patientId/treatments/:treatmentId',
    title: 'Plan Terapéutico | Rehab Elite',
    loadComponent: () => import('./clinical-records/treatment-page').then((m) => m.TreatmentPage),
  },
  {
    path: 'patients/:patientId/treatments/:treatmentId/sessions',
    title: 'Sesiones del Tratamiento | Rehab Elite',
    loadComponent: () =>
      import('./sessions/therapy-session-list').then((m) => m.TherapySessionList),
  },
  {
    path: 'patients/:patientId/treatments/:treatmentId/timeline',
    title: 'Línea de Tiempo | Rehab Elite',
    loadComponent: () => import('./sessions/therapy-timeline').then((m) => m.TherapyTimeline),
  },
  {
    path: 'patients/:patientId/treatments/:treatmentId/sessions/new',
    title: 'Registrar Sesión | Rehab Elite',
    loadComponent: () =>
      import('./sessions/therapy-session-form').then((m) => m.TherapySessionForm),
  },
  {
    path: 'patients/:patientId/treatments/:treatmentId/sessions/:sessionId/edit',
    title: 'Corregir Sesión | Rehab Elite',
    loadComponent: () =>
      import('./sessions/therapy-session-form').then((m) => m.TherapySessionForm),
  },
  {
    path: 'clinical-records',
    title: 'Historial Clínico | Rehab Elite',
    component: PlaceholderPage,
    data: {
      heading: 'Expediente e Historial Clínico de Fisioterapia',
      subtitle:
        'Información diagnóstica, evaluación del dolor, hábitos, antecedentes y plan terapéutico',
      icon: 'description',
    } satisfies PlaceholderPageData,
  },
  // The timeline belongs to a treatment (HU-11): the menu entry opens the patients to choose one
  {
    path: 'timeline',
    title: 'Línea de Tiempo | Rehab Elite',
    loadComponent: () => import('./sessions/timeline-entry').then((m) => m.TimelineEntry),
  },
  {
    path: 'ai-assistant',
    title: 'Asistente Clínico IA | Rehab Elite',
    component: PlaceholderPage,
    data: {
      heading: 'Asistente Clínico de Inteligencia Artificial',
      subtitle:
        'Síntesis de la evolución terapéutica asistida por IA, validada por el fisioterapeuta',
      icon: 'neurology',
    } satisfies PlaceholderPageData,
  },
];
