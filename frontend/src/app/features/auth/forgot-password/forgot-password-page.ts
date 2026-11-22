import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { AccountApi } from '../account-api';

/**
 * Public page /olvide-contrasena (MEJ-01): asks for a password reset link. It shows the API's answer, which is
 * the same whether or not the email belongs to an account.
 */
@Component({
  selector: 'app-forgot-password-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './forgot-password-page.html',
  styleUrl: './forgot-password-page.scss',
})
export class ForgotPasswordPage {
  private readonly api = inject(AccountApi);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });

  protected readonly submitting = signal(false);
  /** The answer of the API once the request was accepted. */
  protected readonly sentMessage = signal<string | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.api.forgotPassword(this.form.getRawValue().email.trim()).subscribe({
      next: ({ message }) => {
        this.submitting.set(false);
        this.sentMessage.set(message);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.errorMessage.set(
          problemDetailMessage(error, 'No se pudo enviar la solicitud. Intente nuevamente.'),
        );
      },
    });
  }
}
