import { FormControl } from '@angular/forms';
import { isValidNationalId, nationalIdError, nationalIdValidator } from './national-id';

// Synthetic national IDs; each invalid one breaks a single rule (its check digit is otherwise right)
describe('national ID validation', () => {
  it.each([
    ['0100000009', 'first province'],
    ['1710034065', 'Pichincha'],
    ['2405123452', 'last province'],
    ['3001234560', 'registered abroad, check digit 0'],
    ['1750000018', 'third digit 5'],
  ])('accepts %s (%s)', (value) => {
    expect(isValidNationalId(value)).toBe(true);
  });

  it.each([
    ['171003406', 'format'],
    ['17100340651', 'format'],
    ['17100340A5', 'format'],
    ['0010000008', 'province'],
    ['2510000009', 'province'],
    ['3110000001', 'province'],
    ['1760000008', 'thirdDigit'],
    ['1710034064', 'checkDigit'],
  ])('rejects %s (%s)', (value, error) => {
    expect(nationalIdError(value)).toBe(error);
  });

  it('works as a form validator and leaves empty values to required', () => {
    expect(new FormControl('1710034066', nationalIdValidator).errors).toEqual({
      nationalId: 'checkDigit',
    });
    expect(new FormControl(' 1710034065 ', nationalIdValidator).errors).toBeNull();
    expect(new FormControl('', nationalIdValidator).errors).toBeNull();
  });
});
