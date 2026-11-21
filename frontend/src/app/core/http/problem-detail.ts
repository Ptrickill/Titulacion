import { HttpErrorResponse } from '@angular/common/http';

/** Spanish "detail" of an API problem response, or the fallback when the error carries none. */
export function problemDetailMessage(error: unknown, fallback: string): string {
  const detail =
    error instanceof HttpErrorResponse
      ? (error.error as { detail?: unknown } | null)?.detail
      : undefined;
  return typeof detail === 'string' && detail ? detail : fallback;
}

/**
 * Like {@link problemDetailMessage}, for a request made with `responseType: 'blob'` (a file download): the problem
 * response then arrives as a Blob, which is read as JSON. Anything that is not a problem with a detail gives the
 * fallback.
 */
export async function blobProblemDetailMessage(error: unknown, fallback: string): Promise<string> {
  if (!(error instanceof HttpErrorResponse) || !(error.error instanceof Blob)) {
    return problemDetailMessage(error, fallback);
  }
  try {
    const detail = (JSON.parse(await error.error.text()) as { detail?: unknown } | null)?.detail;
    return typeof detail === 'string' && detail ? detail : fallback;
  } catch {
    return fallback;
  }
}
