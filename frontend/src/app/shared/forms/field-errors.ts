import { AbstractControl, FormGroupDirective, NgForm } from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';

/** Message of each validation error key, in Spanish; the first error of the control that has one is shown. */
export type ErrorMessages = Record<string, string>;

export function firstErrorMessage(
  control: AbstractControl | null | undefined,
  messages: ErrorMessages,
): string | null {
  if (!control?.errors) {
    return null;
  }
  const key = Object.keys(messages).find((name) => control.hasError(name));
  return key ? messages[key] : null;
}

/**
 * Error state of a field whose input is not bound to its form control (the shared fields of MEJ-03): it shows the
 * control's errors only after the field was touched or the form submitted, never when the dialog opens.
 */
export function errorStateOf(control: () => AbstractControl): ErrorStateMatcher {
  return {
    isErrorState: (_: unknown, form: FormGroupDirective | NgForm | null) => {
      const target = control();
      return target.invalid && (target.touched || !!form?.submitted);
    },
  };
}
