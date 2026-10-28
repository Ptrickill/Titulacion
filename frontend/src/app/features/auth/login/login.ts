import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, isDevMode, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { AuthSession } from '../../../core/auth/auth-session';
import { Role, ROLE_HOME, ROLE_LABELS, ROLES } from '../../../core/auth/role';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login implements OnInit {
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly hidePassword = signal(true);

  // TODO(HU-01): remove the preview access once the backend login exists
  protected readonly showPreviewAccess = isDevMode();
  protected readonly roles = ROLES;
  protected readonly roleLabels = ROLE_LABELS;

  ngOnInit(): void {
    const role = this.session.role();
    if (role) {
      void this.router.navigateByUrl(ROLE_HOME[role]);
    }
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.session.login(this.form.getRawValue()).subscribe({
      next: ({ role }) => void this.router.navigateByUrl(ROLE_HOME[role]),
      error: (error: unknown) => {
        this.submitting.set(false);
        this.errorMessage.set(loginErrorMessage(error));
      },
    });
  }

  protected enterAs(role: Role): void {
    this.session.startPreviewSession(role);
    void this.router.navigateByUrl(ROLE_HOME[role]);
  }
}

function loginErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No se pudo conectar con el servidor. Intente nuevamente.';
    }
    if (error.status === 401) {
      return 'Correo o contraseña incorrectos.';
    }
    const detail = (error.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail) {
      return detail;
    }
  }
  return 'No se pudo iniciar sesión. Intente nuevamente.';
}
