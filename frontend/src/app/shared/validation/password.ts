import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/** At least one letter (any alphabet, including ñ and accents) and one digit, as the backend requires. */
export const PASSWORD_PATTERN = /^(?=.*\p{L})(?=.*\d).+$/u;

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 72;

/** Validators of a new password: the same rules as the API (8 to 72 characters, a letter and a digit). */
export const PASSWORD_VALIDATORS: ValidatorFn[] = [
  Validators.required,
  Validators.minLength(PASSWORD_MIN_LENGTH),
  Validators.maxLength(PASSWORD_MAX_LENGTH),
  Validators.pattern(PASSWORD_PATTERN),
];

/** A rule shown next to the password field, ticked as soon as the typed password meets it. */
export interface PasswordRule {
  label: string;
  met: boolean;
}

/** The visible rules for a password and its confirmation (MEJ-01). */
export function passwordRules(password: string, confirmation: string): PasswordRule[] {
  return [
    {
      label: `De ${PASSWORD_MIN_LENGTH} a ${PASSWORD_MAX_LENGTH} caracteres`,
      met: password.length >= PASSWORD_MIN_LENGTH && password.length <= PASSWORD_MAX_LENGTH,
    },
    { label: 'Al menos una letra', met: /\p{L}/u.test(password) },
    { label: 'Al menos un número', met: /\d/.test(password) },
    {
      label: 'Las dos contraseñas coinciden',
      met: password.length > 0 && password === confirmation,
    },
  ];
}

/**
 * Group validator: marks the "passwordConfirmation" control with { mismatch: true } when it differs from
 * "password" (and clears that error otherwise).
 */
export const passwordsMatchValidator: ValidatorFn = (
  group: AbstractControl,
): ValidationErrors | null => {
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
