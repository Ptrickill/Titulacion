import { Routes } from '@angular/router';
import { PlaceholderPage, PlaceholderPageData } from '../../shared/layout/placeholder-page';

export const PHYSIO_ROUTES: Routes = [
  {
    path: '',
    title: 'Inicio | Rehab Elite',
    loadComponent: () => import('./physio-dashboard').then((m) => m.PhysioDashboard),
  },
  {
    path: 'patients',
    title: 'Gestión de Pacientes | Rehab Elite',
    component: PlaceholderPage,
    data: {
      heading: 'Gestión de Pacientes',
      subtitle: 'Registro, expedientes clínicos y control terapéutico',
      icon: 'group',
    } satisfies PlaceholderPageData,
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
  {
    path: 'timeline',
    title: 'Línea de Tiempo | Rehab Elite',
    component: PlaceholderPage,
    data: {
      heading: 'Línea de Tiempo Terapéutica',
      subtitle: 'Registro cronológico de sesiones, escala EVA y actividades terapéuticas',
      icon: 'schedule',
    } satisfies PlaceholderPageData,
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
