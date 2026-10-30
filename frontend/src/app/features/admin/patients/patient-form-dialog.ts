import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { map, Observable } from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { FormSection } from '../../../shared/ui/form-section';
import {
  NATIONAL_ID_MESSAGES,
  NationalIdError,
  nationalIdValidator,
} from '../../../shared/validation/national-id';
import { ageOn, formatAge, todayIsoDate } from './patient-age';
import { EXISTING_PATIENT_ID, Patient, PatientApi, Sex, UpdatePatientRequest } from './patient-api';
import {
  EDUCATION_LEVELS,
  ETHNIC_IDENTIFICATIONS,
  HEALTH_INSURANCES,
  SEX_LABELS,
  SEXES,
} from './patient-options';

/** Digits, spaces and an optional leading "+", as the backend accepts. */
const PHONE_PATTERN = /^\+?[0-9][0-9 ]{5,18}$/;

/** yyyy-MM-dd strings compare correctly as text. */
function notAfter(today: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null =>
    control.value && control.value > today ? { future: true } : null;
}

/** The backend requires a birth date before today and not after the admission date. */
function validBirthDate(today: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string;
    if (!value) {
      return null;
    }
    if (value >= today) {
      return { notPast: true };
    }
    const admission = control.parent?.get('admissionDate')?.value as string | undefined;
    return admission && value > admission ? { afterAdmission: true } : null;
  };
}

/** Without a patient the dialog registers a new one; with a patient it edits its data. */
export interface PatientFormDialogData {
  patient?: Patient;
}

/**
 * Patient admission and personal data form (prototype 11), never clinical data.
 *
 * - "Registrar Nuevo Paciente" (HU-05): the medical record number mirrors the national ID and a national ID that is
 *   already registered shows a notice with a link to the existing patient.
 * - "Editar Datos del Paciente" (HU-06): same form filled with the patient; the national ID and the medical record
 *   number are read-only, since they never change.
 *
 * In both modes the age is calculated from the birth date and shown read-only.
 */
@Component({
  selector: 'app-patient-form-dialog',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    FormSection,
  ],
  templateUrl: './patient-form-dialog.html',
  styleUrl: './patient-form-dialog.scss',
})
export class PatientFormDialog {
  private readonly api = inject(PatientApi);
  private readonly dialogRef = inject<MatDialogRef<PatientFormDialog, Patient>>(MatDialogRef);
  /** The patient being edited, or undefined when registering. */
  protected readonly patient = inject<PatientFormDialogData | null>(MAT_DIALOG_DATA, {
    optional: true,
  })?.patient;
  protected readonly editing = this.patient !== undefined;

  protected readonly today = todayIsoDate();
  protected readonly sexes = SEXES;
  protected readonly sexLabels = SEX_LABELS;
  protected readonly educationLevels = EDUCATION_LEVELS;
  protected readonly healthInsurances = HEALTH_INSURANCES;
  protected readonly ethnicIdentifications = ETHNIC_IDENTIFICATIONS;

  protected readonly form = inject(NonNullableFormBuilder).group({
    admissionDate: [this.today, [Validators.required, notAfter(this.today)]],
    nationalId: ['', [Validators.required, nationalIdValidator]],
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    birthDate: ['', [Validators.required, validBirthDate(this.today)]],
    sex: [null as Sex | null, Validators.required],
    phone: ['', [Validators.required, Validators.maxLength(20), Validators.pattern(PHONE_PATTERN)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    address: ['', [Validators.required, Validators.maxLength(255)]],
    occupation: ['', [Validators.required, Validators.maxLength(100)]],
    educationLevel: ['', Validators.required],
    healthInsurance: ['', Validators.required],
    ethnicSelfIdentification: [''],
    religion: ['', Validators.maxLength(50)],
  });

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  /** Id of the patient that already has the typed national ID (409 from the server). */
  protected readonly existingPatientId = signal<number | null>(null);

  /** The medical record number is the national ID; the server assigns it. */
  protected readonly medicalRecordNumber = toSignal(
    this.form.controls.nationalId.valueChanges.pipe(map((value) => value.trim())),
    { initialValue: this.patient?.medicalRecordNumber ?? '' },
  );

  private readonly birthDate = toSignal(this.form.controls.birthDate.valueChanges, {
    initialValue: this.patient?.birthDate ?? '',
  });
  protected readonly age = computed(() => formatAge(ageOn(this.birthDate(), this.today)));

  constructor() {
    const destroyRef = inject(DestroyRef);
    const { admissionDate, birthDate, nationalId } = this.form.controls;
    admissionDate.valueChanges
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe(() => birthDate.updateValueAndValidity({ emitEvent: false }));
    // A new national ID may be free: the notice only applies to the one the server rejected
    nationalId.valueChanges
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe(() => this.existingPatientId.set(null));

    if (this.patient) {
      const { ethnicSelfIdentification, religion, ...data } = this.patient;
      this.form.patchValue({
        ...data,
        ethnicSelfIdentification: ethnicSelfIdentification ?? '',
        religion: religion ?? '',
      });
      // The national ID never changes once registered
      nationalId.disable();
    }
  }

  /** Message for the national ID rule the value breaks, if any. */
  protected nationalIdMessage(): string | null {
    const rule = this.form.controls.nationalId.getError('nationalId') as
      NationalIdError | undefined;
    return rule ? NATIONAL_ID_MESSAGES[rule] : null;
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const value = this.form.getRawValue();
    const data: UpdatePatientRequest = {
      admissionDate: value.admissionDate,
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      birthDate: value.birthDate,
      sex: value.sex as Sex,
      phone: value.phone.trim(),
      email: value.email.trim(),
      address: value.address.trim(),
      occupation: value.occupation.trim(),
      educationLevel: value.educationLevel,
      healthInsurance: value.healthInsurance,
      ethnicSelfIdentification: value.ethnicSelfIdentification || null,
      religion: value.religion.trim() || null,
    };
    const request = this.patient
      ? this.api.update(this.patient.id, data)
      : this.api.register({ nationalId: value.nationalId.trim(), ...data });
    request.subscribe({
      next: (patient) => this.dialogRef.close(patient),
      error: (error: unknown) => {
        this.submitting.set(false);
        this.showServerError(error);
      },
    });
  }

  private showServerError(error: unknown): void {
    const existingId =
      error instanceof HttpErrorResponse && error.status === 409
        ? (error.error as Record<string, unknown> | null)?.[EXISTING_PATIENT_ID]
        : undefined;
    if (typeof existingId === 'number') {
      this.existingPatientId.set(existingId);
      this.form.controls.nationalId.setErrors({ duplicate: true });
      this.form.controls.nationalId.markAsTouched();
      return;
    }
    this.errorMessage.set(
      problemDetailMessage(
        error,
        this.editing
          ? 'No se pudieron guardar los cambios. Intente nuevamente.'
          : 'No se pudo registrar el paciente. Intente nuevamente.',
      ),
    );
  }
}

/**
 * Opens the form: registers a new patient, or edits `patient` when given. Emits the saved patient, or undefined
 * if the dialog was cancelled.
 */
export function openPatientFormDialog(
  dialog: MatDialog,
  patient?: Patient,
): Observable<Patient | undefined> {
  return dialog
    .open<PatientFormDialog, PatientFormDialogData, Patient>(PatientFormDialog, {
      data: { patient },
      width: '680px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: patient ? '#patient-first-name' : '#patient-national-id',
      disableClose: true,
    })
    .afterClosed();
}
