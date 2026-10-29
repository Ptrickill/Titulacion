import { Injectable, signal } from '@angular/core';

export const NO_SECTION_ACCESS_MESSAGE = 'No tiene acceso a esa sección.';

/**
 * Set by roleGuard when it sends a user back to their own panel, and shown by AppShell. Keeping the
 * snackbar in the lazily loaded shell keeps Material's overlay out of the initial bundle.
 */
@Injectable({ providedIn: 'root' })
export class SectionAccessNotice {
  private readonly pending = signal(false);

  readonly isPending = this.pending.asReadonly();

  request(): void {
    this.pending.set(true);
  }

  /** Returns true once per request. */
  consume(): boolean {
    if (!this.pending()) {
      return false;
    }
    this.pending.set(false);
    return true;
  }
}
