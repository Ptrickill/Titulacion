import { TextFieldModule } from '@angular/cdk/text-field';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../../../core/auth/auth-service';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { EmptyState } from '../../../shared/ui/empty-state';
import { FormSection } from '../../../shared/ui/form-section';
import { PageHeader } from '../../../shared/ui/page-header';
// GET /api/patients/{id} is shared by the administrator and the physiotherapist (personal data only)
import { PatientApi } from '../../admin/patients/patient-api';
import { CLINICAL_HISTORY_FIELDS, SEVERITIES, SEVERITY_LABELS } from './clinical-labels';
import {
  ClinicalHistoryRequest,
  ClinicalRecordApi,
  EXISTING_CLINICAL_RECORD_ID,
  Icd10Diagnosis,
  Severity,
  TreatmentRequest,
} from './clinical-record-api';
import { Icd10Picker } from './icd10-picker';
import { PatientSummaryCard, SummaryCardPatient } from './patient-summary-card';

/** Same limit as the backend for the free-text columns. */
const TEXT_MAX = 4000;
const WHOLE_NUMBER = /^\d+$/;

const SAVED_MESSAGES: Record<FormMode, string> = {
  record: 'Historial clínico creado correctamente.',
  treatment: 'Tratamiento creado correctamente.',
  edit: 'Historial clínico actualizado correctamente.',
};

/** Route data of the edit route; the other routes are told apart by their parameters. */
export interface ClinicalRecordFormData {
  mode: 'edit';
}

/** New record with its first treatment, new treatment on a record, or edit of the habits and medical history. */
type FormMode = 'record' | 'treatment' | 'edit';

/**
 * Clinical record form of the physiotherapist (HU-07 and HU-09, sections of prototype 04).
 *
 * - `/physio/patients/:patientId/clinical-record/new` creates the clinical record (habits and medical history) with
 *   its first treatment.
 * - `/physio/patients/:patientId/clinical-record/:recordId/treatments/new` opens a new treatment on an existing
 *   record (diagnostic information, pain assessment and therapy plan only).
 * - `/physio/patients/:patientId/clinical-record/edit` edits the habits and medical history only, preloaded from the
 *   record (HU-09); the treatments are not touched.
 *
 * The ICD-10 diagnosis must be chosen from the catalog, the initial EVA is 0–10 and the planned sessions are above 0;
 * the authenticated physiotherapist becomes the current responsible of the treatment. After saving, the form goes to
 * the clinical record view.
 */
@Component({
  selector: 'app-clinical-record-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TextFieldModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    PageHeader,
    FormSection,
    EmptyState,
    Icd10Picker,
    PatientSummaryCard,
  ],
  templateUrl: './clinical-record-form.html',
  styleUrl: './clinical-record-form.scss',
})
export class ClinicalRecordForm {
  private readonly api = inject(ClinicalRecordApi);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  private readonly route = inject(ActivatedRoute).snapshot;
  protected readonly patientId = Number(this.route.paramMap.get('patientId'));
  /** Present when opening a new treatment on an existing record. */
  private readonly recordId = this.route.paramMap.has('recordId')
    ? Number(this.route.paramMap.get('recordId'))
    : null;
  protected readonly mode: FormMode =
    (this.route.data as Partial<ClinicalRecordFormData>).mode === 'edit'
      ? 'edit'
      : this.recordId === null
        ? 'record'
        : 'treatment';
  protected readonly isNewRecord = this.mode === 'record';
  protected readonly isEdit = this.mode === 'edit';
  /** The clinical record view of the patient, where the form returns after saving. */
  protected readonly recordView = `/physio/patients/${this.patientId}/clinical-record`;
  protected readonly backLink = this.isEdit ? this.recordView : '/physio/patients';

  protected readonly textMax = TEXT_MAX;
  protected readonly severities = SEVERITIES;
  protected readonly severityLabels = SEVERITY_LABELS;
  protected readonly historyFields = CLINICAL_HISTORY_FIELDS;
  protected readonly submitLabels: Record<FormMode, string> = {
    record: 'Guardar historial',
    treatment: 'Guardar tratamiento',
    edit: 'Guardar cambios',
  };

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly text = [Validators.required, Validators.maxLength(TEXT_MAX)];

  protected readonly form = this.fb.group({
    history: this.fb.group({
      habits: ['', this.text],
      physicalActivity: ['', this.text],
      allergies: ['', this.text],
      currentMedication: ['', this.text],
      personalHistory: ['', this.text],
      surgicalHistory: ['', this.text],
      familyHistory: ['', this.text],
    }),
    treatment: this.fb.group({
      chiefComplaint: ['', this.text],
      currentIllness: ['', this.text],
      diagnosis: [null as Icd10Diagnosis | null, Validators.required],
      referringPhysician: ['', [Validators.required, Validators.maxLength(150)]],
      complementaryTests: ['', this.text],
      severity: [null as Severity | null, Validators.required],
      painType: ['', [Validators.required, Validators.maxLength(50)]],
      painLocation: ['', [Validators.required, Validators.maxLength(100)]],
      initialPainScore: [
        null as number | null,
        [
          Validators.required,
          Validators.min(0),
          Validators.max(10),
          Validators.pattern(WHOLE_NUMBER),
        ],
      ],
      painFrequency: ['', [Validators.required, Validators.maxLength(50)]],
      sessionFrequency: ['', [Validators.required, Validators.maxLength(50)]],
      goal: ['', this.text],
      plannedSessions: [
        null as number | null,
        [Validators.required, Validators.min(1), Validators.pattern(WHOLE_NUMBER)],
      ],
    }),
  });
  protected readonly treatment = this.form.controls.treatment;

  protected readonly patient = signal<SummaryCardPatient | null>(null);
  /** Why the patient (or, in edit mode, the record) could not be loaded. */
  protected readonly loadError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  /** Id of the record the patient already has (409 from the server). */
  protected readonly existingRecordId = signal<number | null>(null);
  protected readonly responsibleName = computed(() => this.auth.user()?.fullName ?? '');

  constructor() {
    if (this.mode === 'treatment') {
      // A new treatment does not touch the habits and medical history of the record
      this.form.controls.history.disable();
    }
    if (this.isEdit) {
      // Editing the record only touches its habits and medical history
      this.treatment.disable();
      this.api.findRecord(this.patientId).subscribe({
        next: (record) => {
          this.form.controls.history.patchValue(record.clinicalRecord);
          this.patient.set(record.patient);
        },
        error: (error: unknown) =>
          this.loadError.set(
            problemDetailMessage(error, 'Vuelva al expediente e intente nuevamente.'),
          ),
      });
      return;
    }
    inject(PatientApi)
      .findById(this.patientId)
      .subscribe({
        next: (patient) => this.patient.set(patient),
        error: () => this.loadError.set('Vuelva a la lista de pacientes e intente nuevamente.'),
      });
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      this.errorMessage.set('Revise los campos marcados antes de guardar.');
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.request().subscribe({
      next: () => {
        this.snackBar.open(SAVED_MESSAGES[this.mode], 'Cerrar', { duration: 5000 });
        void this.router.navigateByUrl(this.recordView);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.showServerError(error);
      },
    });
  }

  private request(): Observable<unknown> {
    switch (this.mode) {
      case 'edit':
        return this.api.updateHistory(this.patientId, this.historyRequest());
      case 'record':
        return this.api.createRecord(this.patientId, {
          ...this.historyRequest(),
          treatment: this.treatmentRequest(),
        });
      case 'treatment':
        return this.api.openTreatment(this.recordId!, this.treatmentRequest());
    }
  }

  private historyRequest(): ClinicalHistoryRequest {
    const history = this.form.controls.history.getRawValue();
    return {
      habits: history.habits.trim(),
      physicalActivity: history.physicalActivity.trim(),
      allergies: history.allergies.trim(),
      currentMedication: history.currentMedication.trim(),
      personalHistory: history.personalHistory.trim(),
      surgicalHistory: history.surgicalHistory.trim(),
      familyHistory: history.familyHistory.trim(),
    };
  }

  private treatmentRequest(): TreatmentRequest {
    const value = this.treatment.getRawValue();
    return {
      diagnosisCode: value.diagnosis!.code,
      chiefComplaint: value.chiefComplaint.trim(),
      currentIllness: value.currentIllness.trim(),
      referringPhysician: value.referringPhysician.trim(),
      complementaryTests: value.complementaryTests.trim(),
      severity: value.severity!,
      painType: value.painType.trim(),
      painLocation: value.painLocation.trim(),
      initialPainScore: Number(value.initialPainScore),
      painFrequency: value.painFrequency.trim(),
      sessionFrequency: value.sessionFrequency.trim(),
      goal: value.goal.trim(),
      plannedSessions: Number(value.plannedSessions),
    };
  }

  private showServerError(error: unknown): void {
    const existingId =
      error instanceof HttpErrorResponse && error.status === 409
        ? (error.error as Record<string, unknown> | null)?.[EXISTING_CLINICAL_RECORD_ID]
        : undefined;
    if (typeof existingId === 'number') {
      // Notice with a link at the top, and the message next to the buttons where the user is looking
      this.existingRecordId.set(existingId);
      this.errorMessage.set(
        problemDetailMessage(error, 'El paciente ya tiene un historial clínico.'),
      );
      return;
    }
    this.errorMessage.set(
      problemDetailMessage(error, 'No se pudo guardar. Revise los datos e intente nuevamente.'),
    );
  }
}
