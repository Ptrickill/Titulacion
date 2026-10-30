import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Which rule an invalid national ID breaks, in the order they are checked. */
export type NationalIdError = 'format' | 'province' | 'thirdDigit' | 'checkDigit';

const MAX_PROVINCE = 24;
const ABROAD_PROVINCE = 30;

/**
 * Checks an Ecuadorian national ID (cédula) of a natural person with the same rules as the backend: 10 digits,
 * province 01–24 or 30, third digit 0–5 and a modulo 10 check digit. Returns null when it is valid.
 */
export function nationalIdError(value: string): NationalIdError | null {
  if (!/^[0-9]{10}$/.test(value)) {
    return 'format';
  }
  const province = Number(value.slice(0, 2));
  if ((province < 1 || province > MAX_PROVINCE) && province !== ABROAD_PROVINCE) {
    return 'province';
  }
  if (Number(value[2]) > 5) {
    return 'thirdDigit';
  }
  return checkDigit(value) === Number(value[9]) ? null : 'checkDigit';
}

export function isValidNationalId(value: string): boolean {
  return nationalIdError(value) === null;
}

/** Modulo 10: digits weighted 2, 1, 2, 1, ...; products above 9 lose 9; the result completes the next ten. */
function checkDigit(value: string): number {
  let sum = 0;
  for (let i = 0; i < 9; i++) {
    const product = Number(value[i]) * (i % 2 === 0 ? 2 : 1);
    sum += product > 9 ? product - 9 : product;
  }
  return (10 - (sum % 10)) % 10;
}

/** Form validator: `{ nationalId: NationalIdError }` for an invalid value; empty values are left to `required`. */
export const nationalIdValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const value = ((control.value as string | null) ?? '').trim();
  if (!value) {
    return null;
  }
  const error = nationalIdError(value);
  return error ? { nationalId: error } : null;
};

/** Spanish message for each broken rule. */
export const NATIONAL_ID_MESSAGES: Record<NationalIdError, string> = {
  format: 'La cédula debe tener 10 dígitos.',
  province: 'Los dos primeros dígitos deben ser un código de provincia (01 a 24, o 30).',
  thirdDigit: 'El tercer dígito de la cédula debe ser menor que 6.',
  checkDigit: 'El dígito verificador de la cédula no es correcto.',
};
