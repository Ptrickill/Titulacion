import { FormControl } from '@angular/forms';
import { phoneValidator } from './phone';

/** Same cases as the backend's PhoneNumberTests. */
describe('phoneValidator (MEJ-03)', () => {
  const errorOf = (value: string) => new FormControl(value, phoneValidator).errors;

  it('accepts a mobile or a landline with its province code', () => {
    for (const phone of ['0991234567', '0987654321', '022345678', '042345678', '072345678']) {
      expect(errorOf(phone), phone).toBeNull();
    }
  });

  it('rejects everything else', () => {
    for (const phone of [
      '099123456',
      '09912345678',
      '0891234567',
      '012345678',
      '082345678',
      '+593991234567',
      '0991 234 567',
      '099-123-4567',
    ]) {
      expect(errorOf(phone), phone).toEqual({ phone: true });
    }
  });

  it('leaves an empty value to the required validator', () => {
    expect(errorOf('')).toBeNull();
  });
});
