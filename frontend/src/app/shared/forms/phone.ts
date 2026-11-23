import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Same message as the API (MEJ-03). */
export const PHONE_MESSAGE =
  'Ingrese un celular (09XXXXXXXX) o un teléfono fijo con código de provincia';

/**
 * Ecuadorian phone, the same rule as the backend's PhoneNumber: a mobile, 09 and 8 digits, or a landline, 0, the
 * province area code from 2 to 7 and 7 digits. Digits only.
 */
export const PHONE_PATTERN = /^(09\d{8}|0[2-7]\d{7})$/;

/** { phone: true } when the value is not a mobile nor a landline; an empty value is left to `required`. */
export const phoneValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = ((control.value as string | null) ?? '').trim();
  return value && !PHONE_PATTERN.test(value) ? { phone: true } : null;
};
