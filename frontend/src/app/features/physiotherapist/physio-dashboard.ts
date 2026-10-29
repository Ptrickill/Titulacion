import { Component } from '@angular/core';
import { EmptyState } from '../../shared/ui/empty-state';
import { HeroBanner } from '../../shared/ui/hero-banner';

@Component({
  selector: 'app-physio-dashboard',
  imports: [HeroBanner, EmptyState],
  template: `
    <app-hero-banner
      eyebrow="Rehab Elite clínica"
      title="Seguimiento terapéutico centralizado"
      message="Registra pacientes, documenta sesiones y consulta la evolución de cada tratamiento."
    />
    <app-empty-state
      icon="monitoring"
      title="Tu resumen de actividad aparecerá aquí"
      message="Pacientes activos, sesiones registradas y el seguimiento semanal estarán disponibles próximamente."
    />
  `,
})
export class PhysioDashboard {}
