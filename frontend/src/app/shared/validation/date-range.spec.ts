import { FormControl, FormGroup } from '@angular/forms';
import { dateRangeValidator } from './date-range';

describe('dateRangeValidator', () => {
  function group(from: string | null, to: string | null): FormGroup {
    return new FormGroup(
      { from: new FormControl(from), to: new FormControl(to) },
      { validators: dateRangeValidator },
    );
  }

  it('rejects a start after the end', () => {
    expect(group('2026-10-31', '2026-10-01').errors).toEqual({ range: true });
  });

  it('accepts the same day, either date alone and no dates', () => {
    expect(group('2026-10-01', '2026-10-01').valid).toBe(true);
    expect(group('2026-10-01', '2026-10-31').valid).toBe(true);
    expect(group('2026-10-01', null).valid).toBe(true);
    expect(group('', '2026-10-31').valid).toBe(true);
    expect(group(null, null).valid).toBe(true);
  });
});
