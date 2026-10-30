import { Component } from '@angular/core';
import { EmptyState } from '../../shared/ui/empty-state';
import { HeroBanner } from '../../shared/ui/hero-banner';

@Component({
  selector: 'app-admin-dashboard',
  imports: [HeroBanner, EmptyState],
  template: `
    <app-hero-banner
      eyebrow="Rehab Elite clínica"
      title="Seguimiento terapéutico centralizado"
      message="Gestiona usuarios, registra pacientes y consulta los reportes consolidados de la clínica."
    />
    <app-empty-state
      icon="admin_panel_settings"
      title="Las operaciones administrativas aparecerán aquí"
      message="Indicadores, accesos rápidos y seguimiento semanal estarán disponibles próximamente."
    />
  `,
})
export class AdminDashboard {}
