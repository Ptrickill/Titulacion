import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
// GET /api/patients/{id} is shared by the administrator and the physiotherapist (personal data only)
import { Patient, PatientApi } from '../../admin/patients/patient-api';
import { Treatment } from './clinical-record-api';
import { PatientSummaryCard } from './patient-summary-card';
import { TreatmentApi } from './treatment-api';
import { TreatmentPlanCard } from './treatment-plan-card';

const NOT_THIS_PATIENT = 'El tratamiento no pertenece a este paciente.';

/**
 * `/physio/patients/:patientId/treatments/:treatmentId` (HU-08): patient card and "Plan terapéutico y asignación
 * clínica" with the responsible and status changes. The full clinical record view (HU-09) reuses the card.
 */
@Component({
  selector: 'app-treatment-page',
  imports: [
    RouterLink,
    MatButtonModule,
    MatProgressBarModule,
    PageHeader,
    EmptyState,
    PatientSummaryCard,
    TreatmentPlanCard,
  ],
  templateUrl: './treatment-page.html',
  styleUrl: './treatment-page.scss',
})
export class TreatmentPage {
  private readonly params = inject(ActivatedRoute).snapshot.paramMap;
  private readonly patientId = Number(this.params.get('patientId'));
  private readonly treatmentId = Number(this.params.get('treatmentId'));

  protected readonly patient = signal<Patient | null>(null);
  protected readonly treatment = signal<Treatment | null>(null);
  protected readonly loadError = signal<string | null>(null);

  constructor() {
    inject(TreatmentApi)
      .find(this.treatmentId)
      .subscribe({
        next: (treatment) => {
          if (treatment.patientId !== this.patientId) {
            this.loadError.set(NOT_THIS_PATIENT);
            return;
          }
          this.treatment.set(treatment);
        },
        error: (error: unknown) => this.loadError.set(this.messageFor(error)),
      });
    inject(PatientApi)
      .findById(this.patientId)
      .subscribe({
        next: (patient) => this.patient.set(patient),
        error: (error: unknown) => this.loadError.set(this.messageFor(error)),
      });
  }

  private messageFor(error: unknown): string {
    return error instanceof HttpErrorResponse && error.status === 404
      ? problemDetailMessage(error, 'El tratamiento no existe.')
      : 'Revise su conexión e intente nuevamente.';
  }
}
