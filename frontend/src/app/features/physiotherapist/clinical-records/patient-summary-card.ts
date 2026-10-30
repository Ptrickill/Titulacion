import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { formatAge } from '../../admin/patients/patient-age';
import { Patient } from '../../admin/patients/patient-api';
import { SEX_LABELS } from '../../admin/patients/patient-options';

/** Data the card shows: the patient of GET /api/patients/{id} or of the clinical record view (HU-09). */
export type SummaryCardPatient = Pick<
  Patient,
  | 'firstName'
  | 'lastName'
  | 'medicalRecordNumber'
  | 'nationalId'
  | 'age'
  | 'sex'
  | 'occupation'
  | 'phone'
> &
  Partial<Pick<Patient, 'email' | 'address' | 'healthInsurance' | 'admissionDate'>>;

/**
 * Patient card at the top of the physiotherapist's clinical pages (prototype 04): identity and key facts. With
 * `showContact` it adds the admission date and a row with email, address and health insurance (HU-09).
 */
@Component({
  selector: 'app-patient-summary-card',
  imports: [DatePipe, MatIconModule],
  host: { class: 'patient-card', role: 'region', 'aria-label': 'Paciente' },
  template: `
    @let p = patient();
    <div class="main">
      <span class="avatar" aria-hidden="true"
        >{{ p.firstName.charAt(0) }}{{ p.lastName.charAt(0) }}</span
      >
      <div class="identity">
        <span class="hc">HC: {{ p.medicalRecordNumber }}</span>
        <h2 class="name">{{ p.firstName }} {{ p.lastName }}</h2>
        <span class="meta">Cédula: {{ p.nationalId }}</span>
        @if (showContact() && p.admissionDate) {
          <span class="meta">Fecha de ingreso: {{ p.admissionDate | date: 'dd/MM/yyyy' }}</span>
        }
      </div>
      <dl class="facts">
        <div>
          <dt>Edad</dt>
          <dd>{{ formatAge(p.age) }}</dd>
        </div>
        <div>
          <dt>Sexo</dt>
          <dd>{{ sexLabels[p.sex] }}</dd>
        </div>
        <div>
          <dt>Ocupación</dt>
          <dd>{{ p.occupation }}</dd>
        </div>
        <div>
          <dt>Teléfono</dt>
          <dd>{{ p.phone }}</dd>
        </div>
      </dl>
    </div>
    @if (showContact()) {
      <dl class="contact">
        <div>
          <dt>
            <mat-icon aria-hidden="true">mail</mat-icon
            ><span class="cdk-visually-hidden">Correo</span>
          </dt>
          <dd>{{ p.email }}</dd>
        </div>
        <div>
          <dt>
            <mat-icon aria-hidden="true">location_on</mat-icon
            ><span class="cdk-visually-hidden">Dirección</span>
          </dt>
          <dd>{{ p.address }}</dd>
        </div>
        <div>
          <dt>
            <mat-icon aria-hidden="true">verified_user</mat-icon
            ><span class="cdk-visually-hidden">Seguro</span>
          </dt>
          <dd>Seguro: {{ p.healthInsurance }}</dd>
        </div>
      </dl>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 20px 24px;
      background: var(--re-card);
      border: 1px solid var(--re-gray-200);
      border-right: 4px solid var(--re-teal);
      border-radius: var(--re-radius-lg);
      box-shadow: var(--re-shadow-sm);
    }
    .main {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 16px 24px;
    }
    .avatar {
      display: grid;
      flex: none;
      place-items: center;
      width: 52px;
      height: 52px;
      color: var(--re-navy);
      background: var(--re-teal);
      border-radius: var(--re-radius-md);
      font-size: 18px;
      font-weight: 700;
    }
    .identity {
      display: flex;
      flex: 1 1 220px;
      flex-direction: column;
      gap: 2px;
    }
    .hc {
      align-self: flex-start;
      padding: 1px 8px;
      color: var(--re-gray-600);
      background: var(--re-gray-100);
      border-radius: var(--re-radius-sm);
      font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
      font-size: 11px;
    }
    .name {
      margin: 0;
      color: var(--re-text);
      font-size: 20px;
      font-weight: 800;
    }
    .meta {
      color: var(--re-text-muted);
      font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
      font-size: 12px;
    }
    .facts {
      display: grid;
      grid-template-columns: repeat(4, minmax(90px, auto));
      gap: 4px 24px;
      margin: 0;
    }
    .facts dt {
      color: var(--re-text-muted);
      font-size: 11px;
    }
    .facts dd {
      margin: 0;
      color: var(--re-text);
      font-size: 13px;
      font-weight: 700;
    }
    .contact {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 8px 24px;
      margin: 0;
      padding-top: 14px;
      border-top: 1px solid var(--re-gray-200);
    }
    .contact div {
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
    }
    .contact dt {
      display: flex;
      color: var(--re-text-muted);
    }
    .contact mat-icon {
      width: 18px;
      height: 18px;
      font-size: 18px;
    }
    .contact dd {
      margin: 0;
      overflow-wrap: anywhere;
      color: var(--re-gray-700);
      font-size: 13px;
    }
    @media (max-width: 719.98px) {
      .facts {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
      .contact {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class PatientSummaryCard {
  readonly patient = input.required<SummaryCardPatient>();
  /** Adds the admission date and the contact row (full clinical record view). */
  readonly showContact = input(false);

  protected readonly formatAge = formatAge;
  protected readonly sexLabels = SEX_LABELS;
}
