import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth-service';
import { ROLE_HOME } from '../../core/auth/role';
import { EmptyState } from '../ui/empty-state';
import { PageHeader } from '../ui/page-header';

/** Shown when the API rejects a request with 403 because the user's role is not allowed (HU-04). */
@Component({
  selector: 'app-access-denied-page',
  imports: [PageHeader, EmptyState, MatButtonModule, MatIconModule, RouterLink],
  template: `
    <app-page-header
      title="Acceso denegado"
      subtitle="La acción solicitada no está disponible para su rol."
    />
    <app-empty-state
      icon="gpp_bad"
      title="No tiene permisos para esta acción"
      message="El intento quedó registrado en la auditoría. Si necesita este acceso, comuníquese con el administrador del sistema."
    >
      <a mat-flat-button [routerLink]="homeRoute()">
        <mat-icon aria-hidden="true">home</mat-icon>
        Ir a mi panel
      </a>
    </app-empty-state>
  `,
})
export class AccessDeniedPage {
  private readonly auth = inject(AuthService);

  protected readonly homeRoute = computed(() => {
    const role = this.auth.role();
    return role ? ROLE_HOME[role] : '/login';
  });
}
