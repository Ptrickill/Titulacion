import { Component, input } from '@angular/core';
import { formatAge } from '../../admin/patients/patient-age';
import { Patient } from '../../admin/patients/patient-api';
import { SEX_LABELS } from '../../admin/patients/patient-options';

/** Patient card at the top of the physiotherapist's clinical pages (prototype 04): identity and key facts. */
@Component({
  selector: 'app-patient-summary-card',
  host: { class: 'patient-card', role: 'region', 'aria-label': 'Paciente' },
  template: `
    @let p = patient();
    <span class="avatar" aria-hidden="true"
      >{{ p.firstName.charAt(0) }}{{ p.lastName.charAt(0) }}</span
    >
    <div class="identity">
      <span class="hc">HC: {{ p.medicalRecordNumber }}</span>
      <h2 class="name">{{ p.firstName }} {{ p.lastName }}</h2>
      <span class="meta">Cédula: {{ p.nationalId }}</span>
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
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 16px 24px;
      padding: 20px 24px;
      background: var(--re-card);
      border: 1px solid var(--re-gray-200);
      border-right: 4px solid var(--re-teal);
      border-radius: var(--re-radius-lg);
      box-shadow: var(--re-shadow-sm);
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
    dt {
      color: var(--re-text-muted);
      font-size: 11px;
    }
    dd {
      margin: 0;
      color: var(--re-text);
      font-size: 13px;
      font-weight: 700;
    }
    @media (max-width: 719.98px) {
      .facts {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
  `,
})
export class PatientSummaryCard {
  readonly patient = input.required<Patient>();

  protected readonly formatAge = formatAge;
  protected readonly sexLabels = SEX_LABELS;
}
