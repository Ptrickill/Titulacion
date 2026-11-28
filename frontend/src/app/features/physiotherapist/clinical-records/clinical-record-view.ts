import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FileDownload } from '../../../core/http/file-download';
import { blobProblemDetailMessage, problemDetailMessage } from '../../../core/http/problem-detail';
import { todayIsoDate } from '../../../shared/people/patient-age';
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
 * HU-07 in edit mode. "Ver sesiones" opens the sessions of the active treatment, or of the most recent one when none
 * is active (HU-10); "Registrar sesión" opens the session form of the active treatment. MEJ-06: "Exportar historia
 * clínica" downloads the whole record (PDF or JSON) to hand to the data subject.
 */
@Component({
  selector: 'app-clinical-record-view',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
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
  private readonly api = inject(ClinicalRecordApi);
  private readonly fileDownload = inject(FileDownload);
  private readonly snackBar = inject(MatSnackBar);
  /** The export of the record is being downloaded (MEJ-06). */
  protected readonly exporting = signal(false);

  protected readonly historyFields = CLINICAL_HISTORY_FIELDS;
  protected readonly severityLabels = SEVERITY_LABELS;
  protected readonly severityTones = SEVERITY_TONES;
  protected readonly statusLabels = TREATMENT_STATUS_LABELS;
  protected readonly statusTones = TREATMENT_STATUS_TONES;
  protected readonly initials = initialsOf;
  protected readonly painTone = painScoreTone;

  protected readonly record = signal<PatientClinicalRecord | null>(null);
  protected readonly loadError = signal<{ message: string; withoutRecord: boolean } | null>(null);
  /** The active treatment or, without one, the most recent: "Ver sesiones" and "Línea de tiempo" open it. */
  private readonly currentTreatmentId = computed(() => {
    const record = this.record();
    return record?.activeTreatment?.id ?? record?.previousTreatments[0]?.id;
  });

  protected readonly sessionsLink = computed(() => this.treatmentLink('sessions'));
  protected readonly timelineLink = computed(() => this.treatmentLink('timeline'));

  constructor() {
    this.api.findRecord(this.patientId).subscribe({
      next: (record) => this.record.set(record),
      error: (error: unknown) => this.loadError.set(this.errorFor(error)),
    });
  }

  private treatmentLink(page: 'sessions' | 'timeline'): (string | number)[] | null {
    const treatmentId = this.currentTreatmentId();
    return treatmentId === undefined
      ? null
      : ['/physio/patients', this.patientId, 'treatments', treatmentId, page];
  }

  private errorFor(error: unknown): { message: string; withoutRecord: boolean } {
    if (error instanceof HttpErrorResponse && error.status === 404) {
      const message = problemDetailMessage(error, 'El paciente no existe.');
      return { message, withoutRecord: message === WITHOUT_RECORD };
    }
    return { message: 'Revise su conexión e intente nuevamente.', withoutRecord: false };
  }

  /** "Exportar historia clínica" (MEJ-06): the file for the data subject, audited as EXPORT by the API. */
  protected exportRecord(format: 'pdf' | 'json'): void {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    this.api.exportRecord(this.patientId, format).subscribe({
      next: (file) => {
        this.exporting.set(false);
        this.fileDownload.save(
          file,
          `historia-clinica-${this.patientId}-${todayIsoDate()}.${format}`,
        );
      },
      error: (error: unknown) =>
        void blobProblemDetailMessage(
          error,
          'No se pudo exportar la historia clínica. Intente nuevamente.',
        ).then((message) => {
          this.exporting.set(false);
          this.snackBar.open(message, 'Cerrar', { duration: 6000 });
        }),
    });
  }
}
