import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth-service';
import { ROLE_HOME } from '../../core/auth/role';
import { LegalApi } from '../../shared/legal/legal-api';
import { LegalTextView } from '../../shared/legal/legal-text-view';

/**
 * Public page of the privacy notice in force (MEJ-06), `/privacy-notice`: linked from the login page and the patient
 * portal. English route: it does not arrive by email.
 */
@Component({
  selector: 'app-privacy-notice-page',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, RouterLink, LegalTextView],
  template: `
    <main class="page">
      <article class="card" aria-labelledby="privacy-title">
        <p class="brand-name">Rehab<span>Elite</span></p>
        @if (notice.hasValue()) {
          @let text = notice.value();
          <h1 id="privacy-title">{{ text.title }}</h1>
          <app-legal-text-view [text]="text" />
        } @else if (notice.error()) {
          <h1 id="privacy-title">Aviso de privacidad</h1>
          <p class="error" role="alert">
            No se pudo cargar el aviso de privacidad. Intente nuevamente.
          </p>
          <button mat-stroked-button type="button" (click)="notice.reload()">Reintentar</button>
        } @else {
          <h1 id="privacy-title">Aviso de privacidad</h1>
          <mat-spinner diameter="32" aria-label="Cargando el aviso de privacidad" />
        }
        <a mat-button class="back" [routerLink]="backLink">
          <mat-icon aria-hidden="true">arrow_back</mat-icon>
          {{ backLabel }}
        </a>
      </article>
    </main>
  `,
  styles: `
    .page {
      min-height: 100vh;
      padding: var(--re-space-8) var(--re-space-4);
      background: var(--re-page);
      box-sizing: border-box;
    }
    .card {
      max-width: 760px;
      margin: 0 auto;
      padding: var(--re-space-8);
      border-radius: var(--re-radius-lg);
      background: var(--re-card);
      box-shadow: var(--re-shadow-md);
      box-sizing: border-box;
    }
    .brand-name {
      margin: 0 0 var(--re-space-2);
      font-weight: 800;
      color: var(--re-navy);
    }
    .brand-name span {
      color: var(--re-text-teal);
    }
    h1 {
      margin: 0 0 var(--re-space-4);
      font-size: 1.6rem;
      color: var(--re-navy);
    }
    .error {
      color: var(--re-danger);
    }
    .back {
      margin-top: var(--re-space-6);
    }
    @media (max-width: 600px) {
      .card {
        padding: var(--re-space-5) var(--re-space-4);
      }
    }
  `,
})
export class PrivacyNoticePage {
  private readonly api = inject(LegalApi);
  private readonly auth = inject(AuthService);

  protected readonly notice = rxResource({ stream: () => this.api.privacyNotice() });
  protected readonly backLink = this.auth.hasValidSession()
    ? ROLE_HOME[this.auth.role()!]
    : '/login';
  protected readonly backLabel = this.auth.hasValidSession()
    ? 'Volver al sistema'
    : 'Volver al inicio de sesión';
}
