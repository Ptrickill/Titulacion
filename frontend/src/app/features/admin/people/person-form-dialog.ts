import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  AsyncValidatorFn,
  FormGroup,
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
import { ageOn, formatAge, todayIsoDate } from '../patients/patient-age';
import {
  EXISTING_PATIENT_ID,
  Patient,
  PatientAddress,
  PatientApi,
  Sex,
  UpdatePatientRequest,
} from '../patients/patient-api';
import {
  EDUCATION_LEVELS,
  ETHNIC_IDENTIFICATIONS,
  SEX_LABELS,
  SEXES,
} from '../patients/patient-options';
import {
  CONFLICT_FIELD,
  EXISTING_STAFF_ID,
  PeopleApi,
  PersonAccount,
  personLink,
  PersonMatch,
  PersonType,
  StaffRole,
} from './people-api';
import { PERSON_ROLE_LABELS } from './people-labels';
import { PersonKindStep } from './person-kind-step';
import { Staff, StaffApi, UpdateStaffRequest } from './staff-api';

/** yyyy-MM-dd strings compare correctly as text. */
function notAfter(today: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null =>
    control.value && control.value > today ? { future: true } : null;
}

/** The backend requires a birth date before today and, for a patient, not after the admission date. */
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

/** The role is required only for a staff member. */
const roleRequiredForStaff: ValidatorFn = (group: AbstractControl): ValidationErrors | null =>
  group.get('type')?.value === 'STAFF' && !group.get('role')?.value ? { roleRequired: true } : null;

/** Without a person the dialog registers a new one (step 0 chooses the type); with one it edits its data. */
export interface PersonFormDialogData {
  patient?: Patient;
  staff?: Staff;
  /** Preselected type when registering. */
  type?: PersonType;
}

/** What the dialog saved. */
export type PersonFormResult =
  { type: 'PATIENT'; patient: Patient } | { type: 'STAFF'; staff: Staff };

/** A registered person that already has the typed national ID or email. */
interface ExistingRecord {
  type: PersonType;
  id: number;
  name: string;
}

type StepKey = 'kind' | 'identification' | 'personal' | 'admission' | 'review';

/**
 * "Registrar usuario" (HU-05, MEJ-03, MEJ-02): a linear stepper, never clinical data.
 *
 * 0. Tipo de usuario (MEJ-02, only when registering): Paciente or Personal and, for the staff, the role.
 * 1. Identificación: the national ID first, validated while typing. A national ID that a patient or a staff member
 *    already has shows the notice and the link to that record here (GET /api/people/matches). Patients also see
 *    their medical record number live.
 * 2. Datos personales y contacto: names, birth date with the age, sex, phone, email and the structured address.
 *    The email is checked too: a staff member's email must be unique; a patient may share one with other patients
 *    (only a warning), never with the staff.
 * 3. Datos de admisión (patients only): admission date, occupation, education, insurance and the optional data.
 * 4. Revisión: every value with "Editar" per section, and "Registrar". A staff member is registered with its account,
 *    and its owner receives the activation link (MEJ-01).
 *
 * Editing uses the same steps without step 0, not linear. A national ID never changes once registered; a staff
 * record still to be completed receives it here. Changing the email of a person with an account explains what the
 * account will do. Errors show only after a field is touched or "Siguiente" is pressed.
 */
@Component({
  selector: 'app-person-form-dialog',
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
    PersonKindStep,
  ],
  providers: [{ provide: STEPPER_GLOBAL_OPTIONS, useValue: { showError: true } }],
  templateUrl: './person-form-dialog.html',
  styleUrl: './person-form-dialog.scss',
})
export class PersonFormDialog {
  private readonly patientApi = inject(PatientApi);
  private readonly staffApi = inject(StaffApi);
  private readonly peopleApi = inject(PeopleApi);
  private readonly dialogRef =
    inject<MatDialogRef<PersonFormDialog, PersonFormResult>>(MatDialogRef);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly data = inject<PersonFormDialogData | null>(MAT_DIALOG_DATA, { optional: true });

  /** The patient or the staff member being edited; both undefined when registering. */
  protected readonly patient = this.data?.patient;
  protected readonly staff = this.data?.staff;
  protected readonly editing = this.patient !== undefined || this.staff !== undefined;
  /** The account of the person being edited, if any. */
  private readonly account: PersonAccount | null =
    this.patient?.portalAccount ?? this.staff?.account ?? null;
  private readonly originalEmail = (this.patient?.email ?? this.staff?.email ?? '').toLowerCase();

  protected readonly today = todayIsoDate();
  protected readonly sexes = SEXES;
  protected readonly sexLabels = SEX_LABELS;
  protected readonly educationLevels = EDUCATION_LEVELS;
  protected readonly ethnicIdentifications = ETHNIC_IDENTIFICATIONS;
  protected readonly roleLabels = PERSON_ROLE_LABELS;

  private readonly stepper = viewChild.required<MatStepper>('stepper');
  protected readonly catalog = toSignal(
    inject(LocationCatalogApi)
      .catalog()
      .pipe(catchError(() => of(null))),
    { initialValue: null },
  );

  protected readonly kind = this.fb.group(
    {
      type: this.fb.control<PersonType | null>(
        this.patient ? 'PATIENT' : this.staff ? 'STAFF' : (this.data?.type ?? null),
        Validators.required,
      ),
      role: this.fb.control<StaffRole | null>(this.staff?.role ?? null),
    },
    { validators: roleRequiredForStaff },
  );

  /** A national ID that is already registered, found while typing it in step 1. */
  protected readonly existingRecord = signal<ExistingRecord | null>(null);
  protected readonly checkingNationalId = signal(false);
  /** A staff member (or, for the staff, anyone) that already has the typed email. */
  protected readonly emailOwner = signal<ExistingRecord | null>(null);
  /** Other patients with the typed email: a patient may share one, so it is only a warning. */
  protected readonly sharedEmailPatients = signal<string[]>([]);

  /** A registered national ID never changes; a staff record still to be completed receives one. */
  private readonly nationalIdLocked =
    this.patient !== undefined || (this.staff !== undefined && this.staff.nationalId !== null);

  protected readonly identification = this.fb.group({
    nationalId: this.fb.control('', {
      validators: [Validators.required, nationalIdValidator],
      asyncValidators: this.nationalIdLocked ? [] : [this.notRegistered()],
    }),
  });

  protected readonly personal = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    birthDate: ['', [Validators.required, validBirthDate(this.today)]],
    sex: [null as Sex | null, Validators.required],
    phone: ['', [Validators.required, phoneValidator]],
    email: this.fb.control('', {
      validators: [Validators.required, Validators.email, Validators.maxLength(254)],
      asyncValidators: [this.emailAvailable()],
    }),
    address: createAddressForm(addressOf(this.patient?.address ?? this.staff?.address)),
  });

  protected readonly admission = this.fb.group({
    admissionDate: [this.today, [Validators.required, notAfter(this.today)]],
    occupation: ['', [Validators.required, Validators.maxLength(100)]],
    educationLevel: ['', Validators.required],
    hasHealthInsurance: this.fb.control<boolean | null>(null, Validators.required),
    ethnicSelfIdentification: [''],
    religion: ['', Validators.maxLength(50)],
  });

  /** Root of every step, so the birth date can compare itself with the admission date. */
  protected readonly form = this.fb.group({
    kind: this.kind,
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

  protected readonly type = computed<PersonType | null>(() => {
    this.value();
    return this.kind.controls.type.value;
  });
  protected readonly isStaff = computed(() => this.type() === 'STAFF');

  /** Steps in order: the type only when registering, and the admission only for patients. */
  protected readonly steps = computed<StepKey[]>(() => [
    ...(this.editing ? [] : (['kind'] as StepKey[])),
    'identification',
    'personal',
    ...(this.isStaff() ? [] : (['admission'] as StepKey[])),
    'review',
  ]);

  protected readonly subtitle = computed(() => {
    const type = this.type();
    if (type === 'PATIENT') {
      return 'Paciente';
    }
    if (type === 'STAFF') {
      this.value();
      const role = this.kind.controls.role.value;
      return role ? `Personal · ${this.roleLabels[role]}` : 'Personal';
    }
    return 'Paciente o personal';
  });

  /** The medical record number is the national ID; the server assigns it (patients only). */
  protected readonly medicalRecordNumber = computed(() => {
    this.value();
    return this.identification.getRawValue().nationalId.trim();
  });

  protected readonly age = computed(() => {
    this.value();
    return formatAge(ageOn(this.personal.controls.birthDate.value, this.today));
  });

  /** What the account of the person will do with a new email, or null when nothing changes. */
  protected readonly emailChangeNotice = computed(() => {
    this.value();
    const email = this.personal.controls.email.value.trim().toLowerCase();
    if (!this.account || !email || email === this.originalEmail) {
      return null;
    }
    switch (this.account.status) {
      case 'PENDING_ACTIVATION':
        return 'La cuenta aún no se activó: el enlace de activación se enviará al nuevo correo y el anterior dejará de servir.';
      case 'INACTIVE':
        return 'La cuenta está desactivada: actívela desde la columna Cuenta o desde la ficha antes de cambiar el correo.';
      default:
        return 'Se enviará un enlace de confirmación al nuevo correo y un aviso al correo anterior. Hasta que se confirme, la cuenta seguirá ingresando con el correo actual y la misma contraseña.';
    }
  });

  /** Every value as it will be saved, for the review step. */
  protected readonly review = computed(() => {
    this.value();
    const personal = this.personal.getRawValue();
    const admission = this.admission.getRawValue();
    const role = this.kind.controls.role.value;
    return {
      nationalId: this.identification.getRawValue().nationalId.trim(),
      role: role ? this.roleLabels[role] : '',
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
    this.admission.controls.admissionDate.valueChanges.subscribe(() =>
      this.personal.controls.birthDate.updateValueAndValidity({ emitEvent: false }),
    );
    // Another type of person: the email rules change (a patient may share one), so check it again
    this.kind.controls.type.valueChanges.subscribe(() => {
      if (this.personal.controls.email.value) {
        this.personal.controls.email.updateValueAndValidity();
      }
    });

    const person = this.patient ?? this.staff;
    if (person) {
      this.identification.patchValue({ nationalId: person.nationalId ?? '' });
      if (this.nationalIdLocked) {
        this.identification.controls.nationalId.disable();
      }
      this.personal.patchValue({
        firstName: person.firstName,
        lastName: person.lastName,
        birthDate: person.birthDate ?? '',
        sex: person.sex,
        phone: person.phone ?? '',
        email: person.email,
      });
    }
    if (this.patient) {
      const patient = this.patient;
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

  /** Index of a step in the current order, or -1 if it is not shown. */
  protected indexOf(step: StepKey): number {
    return this.steps().indexOf(step);
  }

  /** Message for the national ID rule the value breaks, if any. */
  protected nationalIdMessage(): string | null {
    const rule = this.identification.controls.nationalId.getError('nationalId') as
      NationalIdError | undefined;
    return rule ? NATIONAL_ID_MESSAGES[rule] : null;
  }

  protected link(record: ExistingRecord): (string | number)[] {
    return personLink(record.type, record.id);
  }

  protected isLastStep(): boolean {
    return this.steps()[this.selectedIndex()] === 'review';
  }

  protected previous(): void {
    this.stepper().previous();
  }

  /** Shows the errors of the current step; the linear stepper moves on only when the step is valid. */
  protected next(): void {
    this.groupOf(this.steps()[this.selectedIndex()])?.markAllAsTouched();
    this.stepper().next();
  }

  protected goTo(step: StepKey): void {
    const index = this.indexOf(step);
    if (index >= 0) {
      this.stepper().selectedIndex = index;
    }
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
    const groups = this.steps().filter((step) => step !== 'review');
    const firstInvalid = groups.find((step) => {
      const group = this.groupOf(step);
      return group !== null && (group.invalid || group.pending);
    });
    if (firstInvalid !== undefined) {
      groups.forEach((step) => this.groupOf(step)?.markAllAsTouched());
      this.goTo(firstInvalid);
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const request: Observable<PersonFormResult> = this.isStaff()
      ? this.saveStaff().pipe(map((staff) => ({ type: 'STAFF' as const, staff })))
      : this.savePatient().pipe(map((patient) => ({ type: 'PATIENT' as const, patient })));
    request.subscribe({
      next: (result) => this.dialogRef.close(result),
      error: (error: unknown) => {
        this.submitting.set(false);
        this.showServerError(error);
      },
    });
  }

  private savePatient(): Observable<Patient> {
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
    return this.patient
      ? this.patientApi.update(this.patient.id, data)
      : this.patientApi.register({
          nationalId: this.identification.getRawValue().nationalId.trim(),
          ...data,
        });
  }

  private saveStaff(): Observable<Staff> {
    const personal = this.personal.getRawValue();
    const data: UpdateStaffRequest = {
      nationalId: this.identification.getRawValue().nationalId.trim(),
      firstName: nameCase(personal.firstName),
      lastName: nameCase(personal.lastName),
      birthDate: personal.birthDate,
      sex: personal.sex as Sex,
      phone: personal.phone.trim(),
      email: personal.email.trim(),
      address: addressValueOf(this.personal.controls.address),
    };
    return this.staff
      ? this.staffApi.update(this.staff.id, data)
      : this.staffApi.register({ ...data, role: this.kind.controls.role.value as StaffRole });
  }

  private groupOf(step: StepKey | undefined): FormGroup | null {
    switch (step) {
      case 'kind':
        return this.kind;
      case 'identification':
        return this.identification;
      case 'personal':
        return this.personal;
      case 'admission':
        return this.admission;
      default:
        return null;
    }
  }

  /** The person being edited, so it never matches itself. */
  private isSelf(match: PersonMatch): boolean {
    return (
      (match.type === 'PATIENT' && match.id === this.patient?.id) ||
      (match.type === 'STAFF' && match.id === this.staff?.id)
    );
  }

  /**
   * Async check of step 1: a patient or a staff member already has the national ID. Waits 300 ms after typing; the
   * step stays pending (and "Siguiente" blocked) while it checks.
   */
  private notRegistered(): AsyncValidatorFn {
    return (control: AbstractControl): Observable<ValidationErrors | null> => {
      const nationalId = (control.value as string).trim();
      this.existingRecord.set(null);
      if (nationalIdValidator(control) !== null || !nationalId) {
        return of(null);
      }
      return timer(300).pipe(
        switchMap(() => {
          this.checkingNationalId.set(true);
          return this.peopleApi.matches({ nationalId });
        }),
        map((matches) => {
          this.checkingNationalId.set(false);
          const match = matches.find(
            (candidate) => candidate.field === 'nationalId' && !this.isSelf(candidate),
          );
          if (!match) {
            return null;
          }
          this.existingRecord.set({ type: match.type, id: match.id, name: match.fullName });
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

  /**
   * Async check of the email (MEJ-02): the email of a staff member is unique against patients, staff and accounts;
   * a patient's email must not be a staff member's, and other patients with it only get a warning. An unchanged
   * email of the person being edited is not checked.
   */
  private emailAvailable(): AsyncValidatorFn {
    return (control: AbstractControl): Observable<ValidationErrors | null> => {
      const email = (control.value as string).trim().toLowerCase();
      this.emailOwner.set(null);
      this.sharedEmailPatients.set([]);
      if (
        !email ||
        Validators.email(control) !== null ||
        (this.editing && email === this.originalEmail)
      ) {
        return of(null);
      }
      return timer(300).pipe(
        switchMap(() => this.peopleApi.matches({ email })),
        map((matches) => {
          const others = matches.filter((match) => match.field === 'email' && !this.isSelf(match));
          const blocking = this.isStaff()
            ? others[0]
            : others.find((match) => match.type === 'STAFF');
          if (blocking) {
            this.emailOwner.set({ type: blocking.type, id: blocking.id, name: blocking.fullName });
            return { emailTaken: true };
          }
          this.sharedEmailPatients.set(others.map((match) => match.fullName));
          return null;
        }),
        // Without the check the server still rejects a duplicate (409) when saving
        catchError(() => of(null)),
      );
    };
  }

  private showServerError(error: unknown): void {
    const problem =
      error instanceof HttpErrorResponse && error.status === 409
        ? (error.error as Record<string, unknown> | null)
        : null;
    const patientId = problem?.[EXISTING_PATIENT_ID];
    const staffId = problem?.[EXISTING_STAFF_ID];
    const existing: ExistingRecord | null =
      typeof patientId === 'number'
        ? { type: 'PATIENT', id: patientId, name: '' }
        : typeof staffId === 'number'
          ? { type: 'STAFF', id: staffId, name: '' }
          : null;
    if (problem && problem[CONFLICT_FIELD] === 'email') {
      this.emailOwner.set(existing);
      this.personal.controls.email.setErrors({ emailTaken: true });
      this.personal.controls.email.markAsTouched();
      this.errorMessage.set(problemDetailMessage(error, 'El correo ya está registrado.'));
      this.goTo('personal');
      return;
    }
    if (existing) {
      this.existingRecord.set(existing);
      this.identification.controls.nationalId.setErrors({ duplicate: true });
      this.identification.controls.nationalId.markAsTouched();
      this.goTo('identification');
      return;
    }
    this.errorMessage.set(
      problemDetailMessage(
        error,
        this.editing
          ? 'No se pudieron guardar los cambios. Intente nuevamente.'
          : 'No se pudo registrar a la persona. Intente nuevamente.',
      ),
    );
  }
}

/** The address of a person as the form takes it; an incomplete one keeps only its reference. */
function addressOf(address: PatientAddress | undefined) {
  if (!address) {
    return null;
  }
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
 * Opens the form: registers a new person (step 0 chooses the type), or edits the given patient or staff member.
 * Emits what was saved, or undefined if the dialog was cancelled.
 */
export function openPersonFormDialog(
  dialog: MatDialog,
  data: PersonFormDialogData = {},
): Observable<PersonFormResult | undefined> {
  return dialog
    .open<PersonFormDialog, PersonFormDialogData, PersonFormResult>(PersonFormDialog, {
      data,
      width: '820px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: 'dialog',
      disableClose: true,
    })
    .afterClosed();
}
