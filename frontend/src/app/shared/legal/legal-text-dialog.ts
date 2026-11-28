import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { LegalText } from './legal-api';
import { LegalTextView } from './legal-text-view';

/** A legal text to accept: the button and the statement the user must check first. */
export interface LegalTextDialogData {
  text: LegalText;
  /** E.g. "El paciente leyó y firmó este texto" (reception) or "Leí y acepto este texto" (portal). */
  statement: string;
  confirmLabel: string;
}

/**
 * The text in force of a consent and its acceptance (MEJ-06): the confirm button is enabled only after checking the
 * statement. Closes with true once confirmed. Used by the reception (signed on paper) and by the portal.
 */
@Component({
  selector: 'app-legal-text-dialog',
  imports: [MatDialogModule, MatButtonModule, MatCheckboxModule, LegalTextView],
  template: `
    <h2 mat-dialog-title>{{ data.text.title }}</h2>
    <mat-dialog-content>
      <app-legal-text-view [text]="data.text" />
      <mat-checkbox class="statement" [checked]="accepted" (change)="accepted = $event.checked">
        {{ data.statement }} (versión {{ data.text.version }}).
      </mat-checkbox>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="button" [disabled]="!accepted" [mat-dialog-close]="true">
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .statement {
      display: block;
      margin-top: var(--re-space-4);
    }
  `,
})
export class LegalTextDialog {
  protected readonly data = inject<LegalTextDialogData>(MAT_DIALOG_DATA);
  protected accepted = false;
}

export function openLegalTextDialog(
  dialog: MatDialog,
  data: LegalTextDialogData,
): Observable<boolean | undefined> {
  return dialog
    .open<LegalTextDialog, LegalTextDialogData, boolean>(LegalTextDialog, {
      width: '640px',
      maxWidth: 'calc(100vw - 32px)',
      data,
    })
    .afterClosed();
}
