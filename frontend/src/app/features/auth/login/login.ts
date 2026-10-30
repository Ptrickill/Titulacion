import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth-service';
import { ROLE_HOME } from '../../../core/auth/role';

/** Same wording as the backend: never reveals whether the email, the password or the lock failed. */
export const INVALID_CREDENTIALS_MESSAGE =
  'Correo o contraseña incorrectos, o la cuenta está bloqueada temporalmente.';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
    remember: [false],
  });

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly hidePassword = signal(true);

  ngOnInit(): void {
    if (this.auth.hasValidSession()) {
      void this.router.navigateByUrl(ROLE_HOME[this.auth.role()!]);
    }
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const { email, password, remember } = this.form.getRawValue();
    this.auth.login({ email: email.trim(), password }, remember).subscribe({
      next: ({ role }) => void this.router.navigateByUrl(ROLE_HOME[role]),
      error: (error: unknown) => {
        this.submitting.set(false);
        this.errorMessage.set(loginErrorMessage(error));
      },
    });
  }
}

function loginErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No se pudo conectar con el servidor. Intente nuevamente.';
    }
    if (error.status === 401) {
      return INVALID_CREDENTIALS_MESSAGE;
    }
    const detail = (error.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail) {
      return detail;
    }
  }
  return 'No se pudo iniciar sesión. Intente nuevamente.';
}
