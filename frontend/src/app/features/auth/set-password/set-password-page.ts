import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import {
  PASSWORD_VALIDATORS,
  passwordRules,
  passwordsMatchValidator,
} from '../../../shared/validation/password';
import { AccountApi, SetPasswordRequest } from '../account-api';
import { takeLinkToken } from '../link-privacy';

/** Which emailed link the page serves: set in the route data. */
export type SetPasswordMode = 'activate' | 'reset';

/** Same wording as the API for a link that is unknown, used, replaced or expired. */
export const INVALID_LINK_MESSAGE = 'El enlace no es válido o ya venció. Solicite uno nuevo.';

interface ModeTexts {
  title: string;
  intro: string;
  submit: string;
  done: string;
  /** What to do when the link no longer works. */
  invalidHelp: string;
}

const TEXTS: Record<SetPasswordMode, ModeTexts> = {
  activate: {
    title: 'Active su cuenta',
    intro: 'Defina la contraseña con la que ingresará a Rehab Elite.',
    submit: 'Activar cuenta',
    done: 'Su cuenta está activa. Ya puede iniciar sesión con su correo y la contraseña que acaba de definir.',
    invalidHelp: 'Pida al administrador de la clínica que le reenvíe la invitación.',
  },
  reset: {
    title: 'Restablezca su contraseña',
    intro: 'Defina una contraseña nueva para su cuenta de Rehab Elite.',
    submit: 'Guardar contraseña',
    done: 'Su contraseña se restableció y las sesiones abiertas se cerraron. Ingrese con la contraseña nueva.',
    invalidHelp: 'Puede solicitar un enlace nuevo; vence una hora después de enviado.',
  },
};

/**
 * Public pages of the emailed links (MEJ-01): /activar-cuenta sets the first password of a pending account and
 * /restablecer-contrasena sets a new one. The token is read once, removed from the address bar and kept only in
 * memory; while the page is open no request sends a Referer (see {@link takeLinkToken}).
 */
@Component({
  selector: 'app-set-password-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './set-password-page.html',
  styleUrl: './set-password-page.scss',
})
export class SetPasswordPage {
  private readonly api = inject(AccountApi);

  /** Only in memory: never in the URL, the history or the storage. */
  private readonly token = takeLinkToken();

  protected readonly mode: SetPasswordMode =
    inject(ActivatedRoute).snapshot.data['mode'] === 'reset' ? 'reset' : 'activate';
  protected readonly texts = TEXTS[this.mode];

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      password: ['', PASSWORD_VALIDATORS],
      passwordConfirmation: ['', Validators.required],
    },
    { validators: passwordsMatchValidator },
  );

  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.value });
  protected readonly rules = computed(() =>
    passwordRules(this.value().password ?? '', this.value().passwordConfirmation ?? ''),
  );

  protected readonly hidePassword = signal(true);
  protected readonly submitting = signal(false);
  protected readonly done = signal(false);
  /** The link cannot be used: missing token, or the API rejected it. */
  protected readonly invalidLink = signal(this.token === null);
  protected readonly errorMessage = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid || this.submitting() || this.token === null) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const { password, passwordConfirmation } = this.form.getRawValue();
    const request: SetPasswordRequest = { token: this.token, password, passwordConfirmation };
    const call: Observable<void> =
      this.mode === 'activate' ? this.api.activate(request) : this.api.resetPassword(request);
    call.subscribe({
      next: () => {
        this.submitting.set(false);
        this.done.set(true);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const message = problemDetailMessage(
          error,
          'No se pudo guardar la contraseña. Intente nuevamente.',
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
