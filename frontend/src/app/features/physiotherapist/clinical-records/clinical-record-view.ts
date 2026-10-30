import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatusBadge } from '../../../shared/ui/status-badge';
import {
  CLINICAL_HISTORY_FIELDS,
  initialsOf,
  painScoreTone,
  SEVERITY_LABELS,
  SEVERITY_TONES,
  TREATMENT_STATUS_LABELS,
  TREATMENT_STATUS_TONES,
} from './clinical-labels';
import { ClinicalRecordApi, PatientClinicalRecord } from './clinical-record-api';
import { PatientSummaryCard } from './patient-summary-card';

/** Detail of the 404 for a patient that exists but has no clinical record yet. */
const WITHOUT_RECORD = 'El paciente no tiene historial clínico.';

/**
 * `/physio/patients/:patientId/clinical-record` (HU-09, prototype 04): read-only view of the full clinical record
 * before a session. One request brings the patient, the habits and medical history, the active treatment with its
 * current responsible and the previous treatments; the server audits every read. "Editar historial" opens the form of
 * HU-07 in edit mode; "Ver sesiones" stays disabled until the sessions exist (sprint 5).
 */
@Component({
  selector: 'app-clinical-record-view',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    PageHeader,
    EmptyState,
    StatusBadge,
    PatientSummaryCard,
  ],
  templateUrl: './clinical-record-view.html',
  styleUrl: './clinical-record-view.scss',
})
export class ClinicalRecordView {
  protected readonly patientId = Number(inject(ActivatedRoute).snapshot.paramMap.get('patientId'));

  protected readonly historyFields = CLINICAL_HISTORY_FIELDS;
  protected readonly severityLabels = SEVERITY_LABELS;
  protected readonly severityTones = SEVERITY_TONES;
  protected readonly statusLabels = TREATMENT_STATUS_LABELS;
  protected readonly statusTones = TREATMENT_STATUS_TONES;
  protected readonly initials = initialsOf;
  protected readonly painTone = painScoreTone;

  protected readonly record = signal<PatientClinicalRecord | null>(null);
  protected readonly loadError = signal<{ message: string; withoutRecord: boolean } | null>(null);

  constructor() {
    inject(ClinicalRecordApi)
      .findRecord(this.patientId)
      .subscribe({
        next: (record) => this.record.set(record),
        error: (error: unknown) => this.loadError.set(this.errorFor(error)),
      });
  }

  private errorFor(error: unknown): { message: string; withoutRecord: boolean } {
    if (error instanceof HttpErrorResponse && error.status === 404) {
      const message = problemDetailMessage(error, 'El paciente no existe.');
      return { message, withoutRecord: message === WITHOUT_RECORD };
    }
    return { message: 'Revise su conexión e intente nuevamente.', withoutRecord: false };
  }
}
