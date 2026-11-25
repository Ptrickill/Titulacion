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
      message="Registra pacientes, administra su acceso al portal y consulta su auditoría."
    />
    <app-empty-state
      icon="admin_panel_settings"
      title="Las operaciones de recepción aparecerán aquí"
      message="Indicadores, accesos rápidos y seguimiento semanal estarán disponibles próximamente."
    />
  `,
})
export class AdminDashboard {}
