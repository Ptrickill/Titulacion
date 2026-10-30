import { HttpErrorResponse } from '@angular/common/http';

/** Spanish "detail" of an API problem response, or the fallback when the error carries none. */
export function problemDetailMessage(error: unknown, fallback: string): string {
  const detail =
    error instanceof HttpErrorResponse
      ? (error.error as { detail?: unknown } | null)?.detail
      : undefined;
  return typeof detail === 'string' && detail ? detail : fallback;
}
