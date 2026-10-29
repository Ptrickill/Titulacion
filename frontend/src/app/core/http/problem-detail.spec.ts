import { HttpErrorResponse } from '@angular/common/http';
import { problemDetailMessage } from './problem-detail';

describe('problemDetailMessage', () => {
  const fallback = 'No se pudo completar la acción.';

  it('returns the detail of a problem response', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: { title: 'Regla de negocio', detail: 'No puede desactivar su propia cuenta.' },
    });

    expect(problemDetailMessage(error, fallback)).toBe('No puede desactivar su propia cuenta.');
  });

  it('falls back when there is no detail', () => {
    expect(problemDetailMessage(new HttpErrorResponse({ status: 0 }), fallback)).toBe(fallback);
    expect(
      problemDetailMessage(new HttpErrorResponse({ status: 500, error: { detail: '' } }), fallback),
    ).toBe(fallback);
    expect(problemDetailMessage(new Error('boom'), fallback)).toBe(fallback);
  });
});
