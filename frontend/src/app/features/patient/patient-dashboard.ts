import { Component, computed, inject } from '@angular/core';
import { AuthSession } from '../../core/auth/auth-session';
import { EmptyState } from '../../shared/ui/empty-state';
import { HeroBanner } from '../../shared/ui/hero-banner';

@Component({
  selector: 'app-patient-dashboard',
  imports: [HeroBanner, EmptyState],
  template: `
    <app-hero-banner
      eyebrow="Rehab Elite · Tu espacio de bienestar"
      [title]="greeting()"
      message="Aquí podrás consultar el progreso de tu tratamiento y las indicaciones de tu fisioterapeuta."
    />
    <app-empty-state
      icon="self_improvement"
      title="Tu tratamiento aparecerá aquí"
      message="El progreso de sesiones, la evolución del dolor y las indicaciones estarán disponibles próximamente."
    />
  `,
})
export class PatientDashboard {
  private readonly session = inject(AuthSession);

  protected readonly greeting = computed(() => {
    const firstName = this.session.session()?.fullName.split(/\s+/)[0];
    return firstName ? `¡Hola, ${firstName}!` : '¡Hola!';
  });
}
