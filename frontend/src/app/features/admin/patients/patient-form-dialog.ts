import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  AsyncValidatorFn,
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
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatStepper, MatStepperModule } from '@angular/material/stepper';
import { STEPPER_GLOBAL_OPTIONS } from '@angular/cdk/stepper';
import { RouterLink } from '@angular/router';
import { catchError, map, Observable, of, startWith, switchMap, timer } from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import {
  AddressFields,
  addressValueOf,
  createAddressForm,
} from '../../../shared/forms/address-fields';
import { DateField } from '../../../shared/forms/date-field';
import { formatIsoDate } from '../../../shared/forms/ec-date-adapter';
import { formatAddress, LocationCatalogApi } from '../../../shared/forms/location-catalog';
import { phoneValidator } from '../../../shared/forms/phone';
import { PhoneField } from '../../../shared/forms/phone-field';
import { YesNoToggle } from '../../../shared/forms/yes-no-toggle';
import { nameCase } from '../../../shared/text/name-case';
import {
  NATIONAL_ID_MESSAGES,
  NationalIdError,
  nationalIdValidator,
} from '../../../shared/validation/national-id';
import { ageOn, formatAge, todayIsoDate } from './patient-age';
import { EXISTING_PATIENT_ID, Patient, PatientApi, Sex, UpdatePatientRequest } from './patient-api';
import { EDUCATION_LEVELS, ETHNIC_IDENTIFICATIONS, SEX_LABELS, SEXES } from './patient-options';

/** yyyy-MM-dd strings compare correctly as text. */
function notAfter(today: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null =>
    control.value && control.value > today ? { future: true } : null;
}

/** The backend requires a birth date before today and not after the admission date (in step 3). */
function validBirthDate(today: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string;
    if (!value) {
      return null;
    }
    if (value >= today) {
      return { notPast: true };
    }
    const admission = control.root.get('admission.admissionDate')?.value as string | undefined;
    return admission && value > admission ? { afterAdmission: true } : null;
  };
}

/** Without a patient the dialog registers a new one; with a patient it edits its data. */
export interface PatientFormDialogData {
  patient?: Patient;
}

/** A registered patient that already has the typed national ID (MEJ-03, step 1). */
interface ExistingPatient {
  id: number;
  name: string;
}

/** Index of each step of the stepper. */
const STEP = { identification: 0, personal: 1, admission: 2, review: 3 } as const;

/**
 * "Registrar usuario · Paciente" (HU-05, redesigned in MEJ-03): a linear stepper of four steps, never clinical
 * data.
 *
 * 1. Identificación: the national ID first, validated while typing, with the medical record number shown live.
 *    A national ID that is already registered shows the notice and the link to the existing patient here,
 *    before the rest is filled (GET /api/patients?q= and an exact match).
 * 2. Datos personales y contacto: names (stored with a capital initial), birth date with the age, sex, phone,
 *    email and the structured address.
 * 3. Datos de admisión: admission date (today), occupation, education, "¿Tiene seguro de salud?" and the
 *    optional data (ethnic self-identification and religion) in a collapsible section.
 * 4. Revisión: every value with "Editar" per section, and "Registrar".
 *
 * Editing (HU-06) uses the same steps, not linear, with the national ID read-only. Errors show only after a field
 * is touched or "Siguiente" is pressed; "Siguiente" moves on only when the step is valid.
 */
@Component({
  selector: 'app-patient-form-dialog',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatDialogModule,
    MatStepperModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    AddressFields,
    DateField,
    PhoneField,
    YesNoToggle,
  ],
  providers: [{ provide: STEPPER_GLOBAL_OPTIONS, useValue: { showError: true } }],
  templateUrl: './patient-form-dialog.html',
  styleUrl: './patient-form-dialog.scss',
})
export class PatientFormDialog {
  private readonly api = inject(PatientApi);
  private readonly dialogRef = inject<MatDialogRef<PatientFormDialog, Patient>>(MatDialogRef);
  private readonly fb = inject(NonNullableFormBuilder);
  /** The patient being edited, or undefined when registering. */
  protected readonly patient = inject<PatientFormDialogData | null>(MAT_DIALOG_DATA, {
    optional: true,
  })?.patient;
  protected readonly editing = this.patient !== undefined;
  protected readonly step = STEP;

  protected readonly today = todayIsoDate();
  protected readonly sexes = SEXES;
  protected readonly sexLabels = SEX_LABELS;
  protected readonly educationLevels = EDUCATION_LEVELS;
  protected readonly ethnicIdentifications = ETHNIC_IDENTIFICATIONS;

  private readonly stepper = viewChild.required<MatStepper>('stepper');
  protected readonly catalog = toSignal(
    inject(LocationCatalogApi)
      .catalog()
      .pipe(catchError(() => of(null))),
    { initialValue: null },
  );

  /** A national ID that is already registered, found while typing it in step 1. */
  protected readonly existingPatient = signal<ExistingPatient | null>(null);
  protected readonly checkingNationalId = signal(false);

  protected readonly identification = this.fb.group({
    nationalId: this.fb.control('', {
      validators: [Validators.required, nationalIdValidator],
      asyncValidators: this.editing ? [] : [this.notRegistered()],
    }),
  });

  protected readonly personal = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    birthDate: ['', [Validators.required, validBirthDate(this.today)]],
    sex: [null as Sex | null, Validators.required],
    phone: ['', [Validators.required, phoneValidator]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    address: createAddressForm(this.patient?.address ? addressOf(this.patient) : null),
  });

  protected readonly admission = this.fb.group({
    admissionDate: [this.today, [Validators.required, notAfter(this.today)]],
    occupation: ['', [Validators.required, Validators.maxLength(100)]],
    educationLevel: ['', Validators.required],
    hasHealthInsurance: this.fb.control<boolean | null>(null, Validators.required),
    ethnicSelfIdentification: [''],
    religion: ['', Validators.maxLength(50)],
  });

  protected readonly form = this.fb.group({
    identification: this.identification,
    personal: this.personal,
    admission: this.admission,
  });

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly selectedIndex = signal(0);

  private readonly value = toSignal(this.form.valueChanges.pipe(startWith(null)), {
    initialValue: null,
  });

  /** The medical record number is the national ID; the server assigns it. */
  protected readonly medicalRecordNumber = computed(() => {
    this.value();
    return this.identification.getRawValue().nationalId.trim();
  });

  protected readonly age = computed(() => {
    this.value();
    return formatAge(ageOn(this.personal.controls.birthDate.value, this.today));
  });

  /** Every value as it will be saved, for the review step. */
  protected readonly review = computed(() => {
    this.value();
    const personal = this.personal.getRawValue();
    const admission = this.admission.getRawValue();
    return {
      nationalId: this.identification.getRawValue().nationalId.trim(),
      firstName: nameCase(personal.firstName),
      lastName: nameCase(personal.lastName),
      birthDate: formatIsoDate(personal.birthDate),
      age: formatAge(ageOn(personal.birthDate, this.today)),
      sex: personal.sex ? this.sexLabels[personal.sex] : '',
      phone: personal.phone.trim(),
      email: personal.email.trim().toLowerCase(),
      address: formatAddress(this.catalog(), addressValueOf(this.personal.controls.address)),
      admissionDate: formatIsoDate(admission.admissionDate),
      occupation: admission.occupation.trim(),
      educationLevel: admission.educationLevel,
      hasHealthInsurance:
        admission.hasHealthInsurance === null ? '' : admission.hasHealthInsurance ? 'Sí' : 'No',
      ethnicSelfIdentification: admission.ethnicSelfIdentification || 'No indicada',
      religion: admission.religion.trim() || 'No indicada',
    };
  });

  constructor() {
    const { admissionDate } = this.admission.controls;
    admissionDate.valueChanges.subscribe(() =>
      this.personal.controls.birthDate.updateValueAndValidity({ emitEvent: false }),
    );

    if (this.patient) {
      const patient = this.patient;
      this.identification.patchValue({ nationalId: patient.nationalId });
      // The national ID never changes once registered
      this.identification.controls.nationalId.disable();
      this.personal.patchValue({
        firstName: patient.firstName,
        lastName: patient.lastName,
        birthDate: patient.birthDate,
        sex: patient.sex,
        phone: patient.phone,
        email: patient.email,
      });
      this.admission.patchValue({
        admissionDate: patient.admissionDate,
        occupation: patient.occupation,
        educationLevel: patient.educationLevel,
        hasHealthInsurance: patient.hasHealthInsurance,
        ethnicSelfIdentification: patient.ethnicSelfIdentification ?? '',
        religion: patient.religion ?? '',
      });
    }
  }

  /** Message for the national ID rule the value breaks, if any. */
  protected nationalIdMessage(): string | null {
    const rule = this.identification.controls.nationalId.getError('nationalId') as
      NationalIdError | undefined;
    return rule ? NATIONAL_ID_MESSAGES[rule] : null;
  }

  protected isLastStep(): boolean {
    return this.selectedIndex() === STEP.review;
  }

  protected previous(): void {
    this.stepper().previous();
  }

  /** Shows the errors of the current step; the linear stepper moves on only when the step is valid. */
  protected next(): void {
    this.groupOf(this.selectedIndex())?.markAllAsTouched();
    this.stepper().next();
  }

  protected goTo(index: number): void {
    this.stepper().selectedIndex = index;
  }

  protected submit(): void {
    if (this.submitting()) {
      return;
    }
    // Enter in a field before the review step means "Siguiente", never "Registrar"
    if (!this.isLastStep()) {
      this.next();
      return;
    }
    if (this.form.invalid || this.form.pending) {
      this.form.markAllAsTouched();
      const firstInvalid = [STEP.identification, STEP.personal, STEP.admission].find(
        (index) => this.groupOf(index)?.invalid,
      );
      if (firstInvalid !== undefined) {
        this.goTo(firstInvalid);
      }
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const personal = this.personal.getRawValue();
    const admission = this.admission.getRawValue();
    const data: UpdatePatientRequest = {
      admissionDate: admission.admissionDate,
      firstName: nameCase(personal.firstName),
      lastName: nameCase(personal.lastName),
      birthDate: personal.birthDate,
      sex: personal.sex as Sex,
      phone: personal.phone.trim(),
      email: personal.email.trim(),
      address: addressValueOf(this.personal.controls.address),
      occupation: admission.occupation.trim(),
      educationLevel: admission.educationLevel,
      hasHealthInsurance: admission.hasHealthInsurance === true,
      ethnicSelfIdentification: admission.ethnicSelfIdentification || null,
      religion: admission.religion.trim() || null,
    };
    const request = this.patient
      ? this.api.update(this.patient.id, data)
      : this.api.register({
          nationalId: this.identification.getRawValue().nationalId.trim(),
          ...data,
        });
    request.subscribe({
      next: (patient) => this.dialogRef.close(patient),
      error: (error: unknown) => {
        this.submitting.set(false);
        this.showServerError(error);
      },
    });
  }

  private groupOf(index: number): AbstractControl | null {
    return [this.identification, this.personal, this.admission][index] ?? null;
  }

  /**
   * Async check of step 1: a national ID with its exact match among the search results is already registered.
   * Waits 300 ms after typing; the step stays pending (and "Siguiente" blocked) while it checks.
   */
  private notRegistered(): AsyncValidatorFn {
    return (control: AbstractControl): Observable<ValidationErrors | null> => {
      const nationalId = (control.value as string).trim();
      this.existingPatient.set(null);
      if (nationalIdValidator(control) !== null || !nationalId) {
        return of(null);
      }
      return timer(300).pipe(
        switchMap(() => {
          this.checkingNationalId.set(true);
          return this.api.search(nationalId, 0, 10);
        }),
        map((page) => {
          this.checkingNationalId.set(false);
          const match = page.content.find((patient) => patient.nationalId === nationalId);
          if (!match) {
            return null;
          }
          this.existingPatient.set({ id: match.id, name: `${match.firstName} ${match.lastName}` });
          return { duplicate: true };
        }),
        // Without the check the server still rejects a duplicate (409) when registering
        catchError(() => {
          this.checkingNationalId.set(false);
          return of(null);
        }),
      );
    };
  }

  private showServerError(error: unknown): void {
    const existingId =
      error instanceof HttpErrorResponse && error.status === 409
        ? (error.error as Record<string, unknown> | null)?.[EXISTING_PATIENT_ID]
        : undefined;
    if (typeof existingId === 'number') {
      this.existingPatient.set({ id: existingId, name: '' });
      this.identification.controls.nationalId.setErrors({ duplicate: true });
      this.identification.controls.nationalId.markAsTouched();
      this.goTo(STEP.identification);
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

/** The address of a patient as the form takes it; an incomplete one keeps only its reference. */
function addressOf(patient: Patient) {
  const address = patient.address;
  return {
    provinceCode: address.provinceCode ?? '',
    cantonCode: address.cantonCode ?? '',
    parishCode: address.parishCode ?? '',
    mainStreet: address.mainStreet ?? '',
    streetNumber: address.streetNumber ?? '',
    crossStreet: address.crossStreet ?? '',
    reference: address.reference ?? '',
  };
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
      width: '820px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: patient ? 'dialog' : '#patient-national-id',
      disableClose: true,
    })
    .afterClosed();
}
