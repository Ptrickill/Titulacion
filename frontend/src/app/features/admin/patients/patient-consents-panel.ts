import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { filter, finalize, Observable, switchMap } from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import {
  CONSENT_TEXT_KEYS,
  ConsentType,
  LegalApi,
  PatientConsentView,
} from '../../../shared/legal/legal-api';
import { openLegalTextDialog } from '../../../shared/legal/legal-text-dialog';
import {
  CONSENT_CHANNEL_LABELS,
  CONSENT_STATUS_LABELS,
  CONSENT_STATUS_TONES,
  CONSENT_TYPE_LABELS,
} from '../../../shared/legal/privacy-labels';
import { openConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { PatientConsents, PatientPrivacyApi } from './patient-privacy-api';

/**
 * "Consentimientos" of the patient record (MEJ-06): the state of each consent, and "Registrar firma" (the text in
 * force and the confirmation that the patient signed it) or "Revocar". Revoking the consent to process the data
 * closes the portal access and blocks new care; the record emits `changed` so the page reloads the patient.
 */
@Component({
  selector: 'app-patient-consents-panel',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressBarModule, StatusBadge],
  template: `
    @if (consents.hasValue()) {
      @let data = consents.value();
      <ul class="consents" aria-label="Consentimientos del paciente">
        @for (consent of data.consents; track consent.type) {
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
                @case ('PENDING') {
                  Sin registrar.
                  @if (consent.type === 'DATA_PROCESSING') {
                    Sin este consentimiento el paciente no recibe atenciones nuevas ni acceso al
                    portal.
                  }
                }
                @case ('REVOKED') {
                  Revocado el {{ consent.revokedAt | date: 'dd/MM/yyyy, HH:mm' }} ({{
                    channelLabels[consent.revocationChannel!]
                  }}).
                }
                @default {
                  Versión {{ consent.textVersion }} · {{ channelLabels[consent.channel!] }} ·
                  {{ consent.grantedAt | date: 'dd/MM/yyyy, HH:mm' }}.
                  @if (consent.status === 'PREVIOUS_VERSION') {
                    Hay una versión nueva del texto ({{ consent.currentTextVersion }}).
                  }
                }
              }
            </p>
            @if (!blocked()) {
              <div class="actions">
                @if (consent.status !== 'CURRENT') {
                  <button
                    mat-stroked-button
                    type="button"
                    [disabled]="busy() || !textOf(consent.type)"
                    (click)="grant(consent)"
                  >
                    <mat-icon aria-hidden="true">draw</mat-icon>
                    Registrar firma
                  </button>
                }
                @if (consent.status === 'CURRENT' || consent.status === 'PREVIOUS_VERSION') {
                  <button mat-button type="button" [disabled]="busy()" (click)="revoke(consent)">
                    Revocar
                  </button>
                }
              </div>
            }
          </li>
        }
      </ul>
    } @else if (consents.error()) {
      <p class="error" role="alert">No se pudieron cargar los consentimientos.</p>
      <button mat-stroked-button type="button" (click)="consents.reload()">Reintentar</button>
    } @else {
      <mat-progress-bar mode="indeterminate" aria-label="Cargando consentimientos" />
    }
  `,
  styles: `
    .consents {
      display: grid;
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
      margin-top: var(--re-space-3);
    }
    .error {
      color: var(--re-danger);
    }
  `,
})
export class PatientConsentsPanel {
  private readonly api = inject(PatientPrivacyApi);
  private readonly legal = inject(LegalApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly patientId = input.required<number>();
  readonly patientName = input.required<string>();
  /** A blocked patient (accepted erasure) gets no new consent. */
  readonly blocked = input(false);
  /** A consent changed: the page reloads the patient (pending consent, portal account). */
  readonly changed = output<void>();

  protected readonly typeLabels = CONSENT_TYPE_LABELS;
  protected readonly statusLabels = CONSENT_STATUS_LABELS;
  protected readonly statusTones = CONSENT_STATUS_TONES;
  protected readonly channelLabels = CONSENT_CHANNEL_LABELS;
  protected readonly busy = signal(false);

  protected readonly consents = rxResource({
    params: () => this.patientId(),
    stream: ({ params }) => this.api.consents(params),
  });
  private readonly texts = rxResource({ stream: () => this.legal.consentTexts() });
  private readonly textsByKey = computed(
    () =>
      new Map((this.texts.hasValue() ? this.texts.value() : []).map((text) => [text.key, text])),
  );

  protected textOf(type: ConsentType) {
    return this.textsByKey().get(CONSENT_TEXT_KEYS[type]);
  }

  protected grant(consent: PatientConsentView): void {
    const text = this.textOf(consent.type);
    if (!text || this.busy()) {
      return;
    }
    openLegalTextDialog(this.dialog, {
      text,
      statement: 'El paciente leyó y firmó este texto',
      confirmLabel: 'Registrar consentimiento',
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.run(this.api.grant(this.patientId(), consent.type, text.version))),
      )
      .subscribe({
        next: (data) => this.done(data, 'Consentimiento registrado.'),
        error: (error: unknown) => this.fail(error),
      });
  }

  protected revoke(consent: PatientConsentView): void {
    if (this.busy()) {
      return;
    }
    const dataProcessing = consent.type === 'DATA_PROCESSING';
    openConfirmDialog(this.dialog, {
      title: 'Revocar consentimiento',
      message: dataProcessing
        ? `Se revocará el consentimiento de ${this.patientName()} para el tratamiento de sus datos. Su acceso al portal se desactivará y no podrá recibir historias clínicas, tratamientos ni sesiones nuevas; lo ya registrado se conserva.`
        : `Se revocará el consentimiento de ${this.patientName()} para recibir comunicaciones por correo. Los correos de su cuenta del portal se siguen enviando.`,
      confirmLabel: 'Revocar',
      tone: 'danger',
      icon: 'block',
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.run(this.api.revoke(this.patientId(), consent.type))),
      )
      .subscribe({
        next: (data) => this.done(data, 'Consentimiento revocado.'),
        error: (error: unknown) => this.fail(error),
      });
  }

  private run(request: Observable<PatientConsents>): Observable<PatientConsents> {
    this.busy.set(true);
    return request.pipe(finalize(() => this.busy.set(false)));
  }

  private done(data: PatientConsents, message: string): void {
    this.consents.set(data);
    this.changed.emit();
    this.snackBar.open(message, 'Cerrar', { duration: 5000 });
  }

  private fail(error: unknown): void {
    this.snackBar.open(
      problemDetailMessage(error, 'No se pudo guardar el consentimiento. Intente nuevamente.'),
      'Cerrar',
      { duration: 6000 },
    );
  }
}
