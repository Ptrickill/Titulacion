import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { ErrorStateMatcher } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  map,
  merge,
  Observable,
  of,
  switchMap,
} from 'rxjs';
import { Role, ROLES } from '../../../core/auth/role';
import { FormSection } from '../../../shared/ui/form-section';
import { PortalCandidate, UserAccount, UserApi } from './user-api';
import { USER_ROLE_LABELS } from './user-roles';

/** At least one letter (any alphabet, including ñ and accents) and one digit, as the backend requires. */
const PASSWORD_PATTERN = /^(?=.*\p{L})(?=.*\d).+$/u;

const passwordsMatch: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('password')?.value as string;
  const confirmation = group.get('passwordConfirmation');
  if (confirmation && confirmation.value && confirmation.value !== password) {
    confirmation.setErrors({ ...confirmation.errors, mismatch: true });
  } else if (confirmation?.hasError('mismatch')) {
    const { mismatch: _, ...others } = confirmation.errors ?? {};
    confirmation.setErrors(Object.keys(others).length ? others : null);
  }
  return null;
};

/**
 * "Crear usuario" form (HU-02): account data, role and initial password. A PATIENT account must be linked
 * to a registered patient without a portal account, chosen with a search field.
 */
@Component({
  selector: 'app-create-user-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    FormSection,
  ],
  templateUrl: './create-user-dialog.html',
  styleUrl: './create-user-dialog.scss',
})
export class CreateUserDialog {
  private readonly api = inject(UserApi);
  private readonly dialogRef = inject<MatDialogRef<CreateUserDialog, UserAccount>>(MatDialogRef);

  protected readonly roles = ROLES;
  protected readonly roleLabels = USER_ROLE_LABELS;

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      fullName: ['', [Validators.required, Validators.maxLength(150)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      role: [null as Role | null, Validators.required],
      patient: [null as PortalCandidate | null],
      patientSearch: [''],
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(8),
          Validators.maxLength(72),
          Validators.pattern(PASSWORD_PATTERN),
        ],
      ],
      passwordConfirmation: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  protected readonly candidates = signal<PortalCandidate[]>([]);
  protected readonly searchingCandidates = signal(false);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly hidePassword = signal(true);

  /**
   * The search input shows the error of the chosen patient (a separate control), so the message appears
   * when no patient was selected even if some text was typed.
   */
  protected readonly patientErrorMatcher: ErrorStateMatcher = {
    isErrorState: (_control, parentForm) => {
      const patient = this.form.controls.patient;
      return patient.invalid && (patient.touched || !!parentForm?.submitted);
    },
  };

  /** PATIENT accounts need the linked patient field. */
  protected readonly isPatientRole = toSignal(
    this.form.controls.role.valueChanges.pipe(map((role) => role === 'PATIENT')),
    { initialValue: false },
  );

  constructor() {
    const destroyRef = inject(DestroyRef);
    const { role, patient, patientSearch } = this.form.controls;

    role.valueChanges.pipe(takeUntilDestroyed(destroyRef)).subscribe((value) => {
      // The patient is required only for PATIENT accounts
      patient.setValidators(value === 'PATIENT' ? Validators.required : null);
      if (value !== 'PATIENT') {
        patient.setValue(null);
        patientSearch.setValue('', { emitEvent: false });
      }
      patient.updateValueAndValidity();
    });

    // Search when the PATIENT role is chosen (so the form tells at once if no patient is available)
    // and then while the administrator types
    merge(
      role.valueChanges.pipe(
        filter((value) => value === 'PATIENT'),
        map(() => patientSearch.value),
      ),
      patientSearch.valueChanges.pipe(debounceTime(300), distinctUntilChanged()),
    )
      .pipe(
        switchMap((query) => this.searchCandidates(query)),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((candidates) => {
        this.searchingCandidates.set(false);
        this.candidates.set(candidates);
      });
  }

  protected displayCandidate(candidate: PortalCandidate | string | null): string {
    return typeof candidate === 'string' ? candidate : (candidate?.fullName ?? '');
  }

  protected selectPatient(candidate: PortalCandidate): void {
    this.form.controls.patient.setValue(candidate);
    this.form.controls.patientSearch.setValue(candidate.fullName, { emitEvent: false });
    if (!this.form.controls.fullName.value.trim()) {
      this.form.controls.fullName.setValue(candidate.fullName);
    }
  }

  /** Typing again after choosing a patient discards the choice until a new one is selected. */
  protected onPatientSearchInput(): void {
    if (this.form.controls.patient.value) {
      this.form.controls.patient.setValue(null);
    }
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const value = this.form.getRawValue();
    this.api
      .create({
        fullName: value.fullName.trim(),
        email: value.email.trim(),
        role: value.role as Role,
        password: value.password,
        patientId: value.role === 'PATIENT' ? (value.patient?.id ?? null) : null,
      })
      .subscribe({
        next: (user) => this.dialogRef.close(user),
        error: (error: unknown) => {
          this.submitting.set(false);
          this.showServerError(error);
        },
      });
  }

  private searchCandidates(query: string): Observable<PortalCandidate[]> {
    this.searchingCandidates.set(true);
    return this.api.findPortalCandidates(query.trim()).pipe(catchError(() => of([])));
  }

  private showServerError(error: unknown): void {
    const detail =
      error instanceof HttpErrorResponse
        ? (error.error as { detail?: unknown } | null)?.detail
        : undefined;
    const message =
      typeof detail === 'string' && detail
        ? detail
        : 'No se pudo crear el usuario. Intente nuevamente.';
    if (error instanceof HttpErrorResponse && error.status === 422 && message.includes('correo')) {
      this.form.controls.email.setErrors({ server: message });
      this.form.controls.email.markAsTouched();
      return;
    }
    this.errorMessage.set(message);
  }
}

/** Opens the dialog; emits the created account, or undefined if it was cancelled. */
export function openCreateUserDialog(dialog: MatDialog): Observable<UserAccount | undefined> {
  return dialog
    .open<CreateUserDialog, void, UserAccount>(CreateUserDialog, {
      width: '640px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: 'first-tabbable',
      disableClose: true,
    })
    .afterClosed();
}
