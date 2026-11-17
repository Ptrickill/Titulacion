import { HttpErrorResponse } from '@angular/common/http';
import { blobProblemDetailMessage, problemDetailMessage } from './problem-detail';

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

describe('blobProblemDetailMessage', () => {
  const fallback = 'No se pudo exportar el informe.';

  function blobError(status: number, body: string, type = 'application/problem+json') {
    return new HttpErrorResponse({ status, error: new Blob([body], { type }) });
  }

  it('reads the detail of a problem response that arrived as a Blob', async () => {
    const error = blobError(
      400,
      JSON.stringify({ detail: 'La fecha inicial no puede ser posterior a la fecha final.' }),
    );

    expect(await blobProblemDetailMessage(error, fallback)).toBe(
      'La fecha inicial no puede ser posterior a la fecha final.',
    );
  });

  it('falls back when the Blob is not a problem with a detail', async () => {
    expect(await blobProblemDetailMessage(blobError(502, '<html>', 'text/html'), fallback)).toBe(
      fallback,
    );
    expect(await blobProblemDetailMessage(blobError(500, '{}'), fallback)).toBe(fallback);
    expect(await blobProblemDetailMessage(new HttpErrorResponse({ status: 0 }), fallback)).toBe(
      fallback,
    );
  });
});
