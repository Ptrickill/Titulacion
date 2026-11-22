import { FormControl, FormGroup } from '@angular/forms';
import { passwordRules, passwordsMatchValidator, PASSWORD_VALIDATORS } from './password';

describe('password validation', () => {
  it('ticks each visible rule as the password meets it', () => {
    expect(passwordRules('', '').map((rule) => rule.met)).toEqual([false, false, false, false]);
    expect(passwordRules('clave', 'clave').map((rule) => rule.met)).toEqual([
      false,
      true,
      false,
      true,
    ]);
    expect(passwordRules('Clave.Ñandú2026', 'Clave.Ñandú2026').every((rule) => rule.met)).toBe(
      true,
    );
    expect(passwordRules('Clave.2026', 'Clave.2027')[3].met).toBe(false);
  });

  it('applies the same rules as the API', () => {
    const control = new FormControl('', PASSWORD_VALIDATORS);
    for (const invalid of ['', 'corta1', 'SoloLetrasLargas', '12345678', 'a1'.repeat(37)]) {
      control.setValue(invalid);
      expect(control.valid).toBe(false);
    }
    control.setValue('Clave.Segura2026');
    expect(control.valid).toBe(true);
  });

  it('marks a confirmation that differs and clears the error once it matches', () => {
    const group = new FormGroup(
      { password: new FormControl('Clave.2026'), passwordConfirmation: new FormControl('Clave') },
      { validators: passwordsMatchValidator },
    );
    expect(group.controls.passwordConfirmation.hasError('mismatch')).toBe(true);

    group.controls.passwordConfirmation.setValue('Clave.2026');
    expect(group.controls.passwordConfirmation.errors).toBeNull();
  });
});
