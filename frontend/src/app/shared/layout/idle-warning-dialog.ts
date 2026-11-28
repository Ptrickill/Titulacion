import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { IdleSession } from '../../core/auth/idle-session';

/** What the user chose in the warning: keep the session or close it now. */
export type IdleWarningChoice = 'stay' | 'logout';

/**
 * Warning before the close of an idle session (MEJ-06): the countdown and "Seguir conectado". It cannot be dismissed
 * with Escape or a click outside: only an explicit choice keeps the session.
 */
@Component({
  selector: 'app-idle-warning-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title class="title">
      <mat-icon aria-hidden="true">schedule</mat-icon>
      Su sesión está por cerrarse
    </h2>
    <mat-dialog-content>
      <p>
        Por seguridad, la sesión se cierra tras un tiempo sin actividad. Se cerrará en
        <strong class="countdown" aria-live="polite">{{ countdown() }}</strong
        >.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="ref.close('logout')">Cerrar sesión</button>
      <button mat-flat-button type="button" cdkFocusInitial (click)="ref.close('stay')">
        Seguir conectado
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .title {
      display: flex;
      align-items: center;
      gap: var(--re-space-2);
    }
    .countdown {
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class IdleWarningDialog {
  protected readonly ref = inject<MatDialogRef<IdleWarningDialog, IdleWarningChoice>>(MatDialogRef);
  private readonly idle = inject(IdleSession);

  protected readonly countdown = computed(() => {
    const seconds = Math.max(this.idle.secondsLeft(), 0);
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  });
}
