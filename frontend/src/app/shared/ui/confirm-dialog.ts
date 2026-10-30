import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { map, Observable } from 'rxjs';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** "danger" for destructive or irreversible actions (e.g. abandoning a treatment). */
  tone?: 'primary' | 'danger';
  icon?: string;
}

/** Confirmation modal with the navy header used across the prototype. */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <header class="header">
      <div class="icon-tile" [class.danger]="data.tone === 'danger'">
        <mat-icon aria-hidden="true">{{
          data.icon ?? (data.tone === 'danger' ? 'warning' : 'help')
        }}</mat-icon>
      </div>
      <h2 mat-dialog-title class="title">{{ data.title }}</h2>
    </header>
    <mat-dialog-content>
      <p class="message">{{ data.message }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" [mat-dialog-close]="false">
        {{ data.cancelLabel ?? 'Cancelar' }}
      </button>
      <button
        mat-flat-button
        type="button"
        class="confirm"
        [class.danger]="data.tone === 'danger'"
        [mat-dialog-close]="true"
        cdkFocusInitial
      >
        {{ data.confirmLabel ?? 'Confirmar' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px 24px;
      background: var(--re-navy);
    }
    .icon-tile {
      display: grid;
      flex: none;
      place-items: center;
      width: 36px;
      height: 36px;
      color: var(--re-navy);
      background: var(--re-teal);
      border-radius: var(--re-radius-sm);
    }
    .icon-tile.danger {
      color: #ffffff;
      background: var(--re-danger);
    }
    .title {
      margin: 0;
      padding: 0;
      color: #ffffff;
      font-size: 16px;
      font-weight: 700;
    }
    .title::before {
      display: none;
    }
    .message {
      margin: 20px 0 4px;
      color: var(--re-gray-600);
      line-height: 1.5;
    }
    .confirm.danger {
      --mat-button-filled-container-color: var(--re-danger);
      --mat-button-filled-label-text-color: #ffffff;
    }
  `,
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}

/** Opens {@link ConfirmDialog}; emits true only when the user confirms. */
export function openConfirmDialog(dialog: MatDialog, data: ConfirmDialogData): Observable<boolean> {
  return dialog
    .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
      data,
      width: '440px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: 'dialog',
    })
    .afterClosed()
    .pipe(map((result) => result === true));
}
