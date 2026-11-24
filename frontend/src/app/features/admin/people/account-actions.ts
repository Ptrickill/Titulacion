import { inject, Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, EMPTY, filter, map, Observable, switchMap, tap } from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { openConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { PersonAccount } from './people-api';
import { UserAccount, UserApi } from './user-api';

/** The updated account, as the registry and the records show it. */
function toPersonAccount(account: UserAccount): PersonAccount {
  return {
    userId: account.id,
    status: account.status,
    emailChangePending: account.emailChangePending,
    lastLoginAt: account.lastLoginAt,
  };
}

/**
 * The actions on a person's account (HU-03, MEJ-01, MEJ-02), shared by "Registro de Usuarios" and the records of
 * patients and staff: activate or deactivate (after a confirmation) and resend the pending link. Each action shows
 * its notice and the API message when it fails, and emits the updated account (nothing when cancelled or failed).
 */
@Injectable({ providedIn: 'root' })
export class AccountActions {
  private readonly api = inject(UserApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** Asks for confirmation, then activates a deactivated account or deactivates an enabled one. */
  toggleStatus(account: PersonAccount, fullName: string): Observable<UserAccount> {
    const deactivate = account.status !== 'INACTIVE';
    return openConfirmDialog(this.dialog, {
      title: deactivate ? 'Desactivar cuenta' : 'Activar cuenta',
      message: deactivate
        ? `¿Desactivar la cuenta de ${fullName}? No podrá iniciar sesión hasta que la reactive.`
        : `¿Activar la cuenta de ${fullName}? Podrá volver a iniciar sesión.`,
      confirmLabel: deactivate ? 'Desactivar' : 'Activar',
      tone: deactivate ? 'danger' : 'primary',
      icon: deactivate ? 'lock' : 'lock_open',
    }).pipe(
      filter(Boolean),
      switchMap(() => this.api.changeStatus(account.userId, !deactivate)),
      tap((updated) =>
        this.notify(
          updated.active ? 'Cuenta activada correctamente.' : 'Cuenta desactivada correctamente.',
          5000,
        ),
      ),
      catchError((error: unknown) =>
        this.fail(error, 'No se pudo cambiar el estado de la cuenta. Intente nuevamente.'),
      ),
    );
  }

  /** The activation link of a pending account, or the confirmation of a new email. */
  resend(account: PersonAccount, email: string): Observable<UserAccount> {
    return this.api.resendInvitation(account.userId).pipe(
      tap((updated) =>
        this.notify(
          updated.emailChangePending
            ? `Enviamos un nuevo enlace de confirmación a ${email}`
            : `Enviamos un nuevo enlace de activación a ${email}`,
          6000,
        ),
      ),
      catchError((error: unknown) =>
        this.fail(error, 'No se pudo reenviar el enlace. Intente nuevamente.'),
      ),
    );
  }

  /** The same actions, answering the account as a {@link PersonAccount}. */
  toggleStatusOf(account: PersonAccount, fullName: string): Observable<PersonAccount> {
    return this.toggleStatus(account, fullName).pipe(map(toPersonAccount));
  }

  resendOf(account: PersonAccount, email: string): Observable<PersonAccount> {
    return this.resend(account, email).pipe(map(toPersonAccount));
  }

  private notify(message: string, duration: number): void {
    this.snackBar.open(message, 'Cerrar', { duration });
  }

  private fail(error: unknown, fallback: string): Observable<never> {
    this.notify(problemDetailMessage(error, fallback), 6000);
    return EMPTY;
  }
}
