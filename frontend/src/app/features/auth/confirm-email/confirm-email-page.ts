import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { AccountApi } from '../account-api';
import { takeLinkToken } from '../link-privacy';
import { INVALID_LINK_MESSAGE } from '../set-password/set-password-page';

/**
 * Public page of the "Confirme su nuevo correo" link (MEJ-02): /confirmar-correo. The administrator changed the
 * email of the account; once the owner confirms it here, it becomes the login email and the sessions opened with the
 * previous one are closed. The password does not change.
 *
 * The page asks for a click instead of confirming when it loads, so an email scanner that opens the link does not
 * spend it. The token is protected like the MEJ-01 links ({@link takeLinkToken}).
 */
@Component({
  selector: 'app-confirm-email-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './confirm-email-page.html',
  styleUrl: './confirm-email-page.scss',
})
export class ConfirmEmailPage {
  private readonly api = inject(AccountApi);

  /** Only in memory: never in the URL, the history or the storage. */
  private readonly token = takeLinkToken();

  protected readonly submitting = signal(false);
  protected readonly done = signal(false);
  /** The link cannot be used: missing token, or the API rejected it. */
  protected readonly invalidLink = signal(this.token === null);
  protected readonly errorMessage = signal<string | null>(null);

  protected confirm(): void {
    if (this.submitting() || this.token === null) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.api.confirmEmail(this.token).subscribe({
      next: () => {
        this.submitting.set(false);
        this.done.set(true);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const message = problemDetailMessage(
          error,
          'No se pudo confirmar el correo. Intente nuevamente.',
        );
        if (message === INVALID_LINK_MESSAGE) {
          this.invalidLink.set(true);
        } else {
          this.errorMessage.set(message);
        }
      },
    });
  }
}
