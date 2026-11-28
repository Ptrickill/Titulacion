import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { filter, finalize, Observable, switchMap } from 'rxjs';
import { problemDetailMessage } from '../../core/http/problem-detail';
import {
  CONSENT_TEXT_KEYS,
  ConsentType,
  LegalApi,
  PatientConsentView,
} from '../../shared/legal/legal-api';
import { openLegalTextDialog } from '../../shared/legal/legal-text-dialog';
import {
  CONSENT_STATUS_LABELS,
  CONSENT_STATUS_TONES,
  CONSENT_TYPE_LABELS,
} from '../../shared/legal/privacy-labels';
import { openConfirmDialog } from '../../shared/ui/confirm-dialog';
import { StatusBadge } from '../../shared/ui/status-badge';
import { PortalApi } from './portal-api';

/**
 * "Mis consentimientos" of the patient portal (MEJ-06): the state of each consent, accepting the text in force (a new
 * version, or the email communications) and revoking the email communications. The consent to process the data is
 * revoked at the reception, because it closes this very access. Links to the privacy notice.
 */
@Component({
  selector: 'app-portal-consents-card',
  imports: [DatePipe, MatButtonModule, MatIconModule, RouterLink, StatusBadge],
  template: `
    <section class="card" aria-labelledby="consents-title">
      <header class="header">
        <h2 id="consents-title" class="title">Mis consentimientos</h2>
        <a class="privacy" routerLink="/privacy-notice">
          <mat-icon aria-hidden="true">policy</mat-icon>
          Aviso de privacidad
        </a>
      </header>
      @if (consents.hasValue()) {
        @let list = consents.value();
        <ul class="consents">
          @for (consent of list; track consent.type) {
            <li class="consent">
              <div class="head">
                <span class="name">{{ typeLabels[consent.type] }}</span>
                <app-status-badge
                  [label]="statusLabels[consent.status]"
                  [tone]="statusTones[consent.status]"
                />
              </div>
              <p class="detail">
                @switch (consent.status) {
                  @case ('CURRENT') {
                    Aceptado el {{ consent.grantedAt | date: 'dd/MM/yyyy' }} (versión
                    {{ consent.textVersion }}).
                  }
                  @case ('PREVIOUS_VERSION') {
                    Aceptaste la versión {{ consent.textVersion }}; hay una versión nueva del texto.
                  }
                  @case ('REVOKED') {
                    Revocado el {{ consent.revokedAt | date: 'dd/MM/yyyy' }}.
                  }
                  @default {
                    Aún no lo has aceptado.
                  }
                }
                @if (consent.type === 'DATA_PROCESSING' && consent.status === 'CURRENT') {
                  Para revocarlo, solicítalo en la recepción de la clínica.
                }
              </p>
              <div class="actions">
                @if (canAccept(consent)) {
                  <button
                    mat-stroked-button
                    type="button"
                    [disabled]="busy() || !textOf(consent.type)"
                    (click)="accept(consent)"
                  >
                    {{
                      consent.status === 'PREVIOUS_VERSION'
                        ? 'Aceptar la versión vigente'
                        : 'Aceptar'
                    }}
                  </button>
                }
                @if (consent.type === 'EMAIL_COMMUNICATIONS' && isValid(consent)) {
                  <button mat-button type="button" [disabled]="busy()" (click)="revoke(consent)">
                    Revocar
                  </button>
                }
              </div>
            </li>
          }
        </ul>
      } @else if (consents.error()) {
        <p class="error" role="alert">No se pudieron cargar tus consentimientos.</p>
      }
    </section>
  `,
  styles: `
    @use './portal-card' as portal;

    .card {
      @include portal.card;
    }
    .header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--re-space-2);
      margin-bottom: var(--re-space-3);
    }
    .title {
      @include portal.title;
    }
    .privacy {
      display: inline-flex;
      align-items: center;
      gap: var(--re-space-1);
      color: var(--re-text-teal);
      font-size: 13px;
      font-weight: 600;
      text-decoration: none;
    }
    .privacy:hover,
    .privacy:focus-visible {
      text-decoration: underline;
    }
    .privacy mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }
    .consents {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
      gap: var(--re-space-3);
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .consent {
      padding: var(--re-space-3) var(--re-space-4);
      border: 1px solid var(--re-gray-200);
      border-radius: var(--re-radius-md);
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--re-space-2);
    }
    .name {
      font-weight: 700;
      color: var(--re-navy);
    }
    .detail {
      margin: var(--re-space-2) 0 0;
      color: var(--re-text-muted);
      font-size: 0.9rem;
      overflow-wrap: anywhere;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--re-space-2);
      margin-top: var(--re-space-2);
    }
    .actions:empty {
      display: none;
    }
    .error {
      color: var(--re-danger);
    }
  `,
})
export class PortalConsentsCard {
  private readonly api = inject(PortalApi);
  private readonly legal = inject(LegalApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly typeLabels = CONSENT_TYPE_LABELS;
  protected readonly statusLabels = CONSENT_STATUS_LABELS;
  protected readonly statusTones = CONSENT_STATUS_TONES;
  protected readonly busy = signal(false);

  protected readonly consents = rxResource({ stream: () => this.api.consents() });
  private readonly texts = rxResource({ stream: () => this.legal.consentTexts() });
  private readonly textsByKey = computed(
    () =>
      new Map((this.texts.hasValue() ? this.texts.value() : []).map((text) => [text.key, text])),
  );

  protected textOf(type: ConsentType) {
    return this.textsByKey().get(CONSENT_TEXT_KEYS[type]);
  }

  protected isValid(consent: PatientConsentView): boolean {
    return consent.status === 'CURRENT' || consent.status === 'PREVIOUS_VERSION';
  }

  /** A new version of either text, or the email communications not accepted yet. */
  protected canAccept(consent: PatientConsentView): boolean {
    return (
      consent.status === 'PREVIOUS_VERSION' ||
      (consent.type === 'EMAIL_COMMUNICATIONS' && consent.status !== 'CURRENT')
    );
  }

  protected accept(consent: PatientConsentView): void {
    const text = this.textOf(consent.type);
    if (!text || this.busy()) {
      return;
    }
    openLegalTextDialog(this.dialog, {
      text,
      statement: 'Leí y acepto este texto',
      confirmLabel: 'Aceptar',
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.run(this.api.acceptConsent(consent.type, text.version))),
      )
      .subscribe({
        next: (list) => this.done(list, 'Consentimiento aceptado.'),
        error: (error: unknown) => this.fail(error),
      });
  }

  protected revoke(consent: PatientConsentView): void {
    if (this.busy()) {
      return;
    }
    openConfirmDialog(this.dialog, {
      title: 'Revocar comunicaciones por correo',
      message:
        'Dejarás de recibir comunicaciones de la clínica por correo. Los correos de tu cuenta (como el restablecimiento de contraseña) se siguen enviando.',
      confirmLabel: 'Revocar',
      tone: 'danger',
      icon: 'unsubscribe',
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.run(this.api.revokeConsent(consent.type))),
      )
      .subscribe({
        next: (list) => this.done(list, 'Consentimiento revocado.'),
        error: (error: unknown) => this.fail(error),
      });
  }

  private run(request: Observable<PatientConsentView[]>): Observable<PatientConsentView[]> {
    this.busy.set(true);
    return request.pipe(finalize(() => this.busy.set(false)));
  }

  private done(list: PatientConsentView[], message: string): void {
    this.consents.set(list);
    this.snackBar.open(message, 'Cerrar', { duration: 5000 });
  }

  private fail(error: unknown): void {
    this.snackBar.open(
      problemDetailMessage(error, 'No se pudo guardar tu consentimiento. Intenta nuevamente.'),
      'Cerrar',
      { duration: 6000 },
    );
  }
}
