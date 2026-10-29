import { Component, computed, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth-service';
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
  private readonly auth = inject(AuthService);

  protected readonly greeting = computed(() => {
    const firstName = this.auth.user()?.fullName.split(/\s+/)[0];
    return firstName ? `¡Hola, ${firstName}!` : '¡Hola!';
  });
}
