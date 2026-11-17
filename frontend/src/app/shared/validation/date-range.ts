import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Same message as the API (400) for a range whose start is after its end. */
export const DATE_RANGE_MESSAGE = 'La fecha inicial no puede ser posterior a la fecha final.';

/**
 * Validator of a form group with the controls `from` and `to` (`yyyy-MM-dd`, so they compare as text): "Desde" after
 * "Hasta" is an error of the whole group, `{ range: true }`. Either date alone, or none, is valid.
 */
export function dateRangeValidator(group: AbstractControl): ValidationErrors | null {
  const from: unknown = group.get('from')?.value;
  const to: unknown = group.get('to')?.value;
  return typeof from === 'string' && typeof to === 'string' && from && to && from > to
    ? { range: true }
    : null;
}
