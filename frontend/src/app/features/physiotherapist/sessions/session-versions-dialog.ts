import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Observable } from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { PROGRESS_STATUS_LABELS, SESSION_STATUS_LABELS } from './session-labels';
import { TherapySessionApi, TherapySessionVersion } from './therapy-session-api';

export interface SessionVersionsDialogData {
  sessionId: number;
  sequenceNumber: number;
}

/**
 * "Ver versiones" (HU-10): every version of a session kept by Hibernate Envers, most recent first, with who made each
 * one and when. Any physiotherapist can open it; the server audits the read.
 */
@Component({
  selector: 'app-session-versions-dialog',
  imports: [DatePipe, MatDialogModule, MatButtonModule, MatProgressBarModule, StatusBadge],
  templateUrl: './session-versions-dialog.html',
  styleUrl: './session-versions-dialog.scss',
})
export class SessionVersionsDialog {
  protected readonly data = inject<SessionVersionsDialogData>(MAT_DIALOG_DATA);
  protected readonly statusLabels = SESSION_STATUS_LABELS;
  protected readonly progressLabels = PROGRESS_STATUS_LABELS;
  protected readonly typeLabels: Record<TherapySessionVersion['type'], string> = {
    CREATE: 'Registro',
    UPDATE: 'Corrección',
    DELETE: 'Eliminación',
  };

  protected readonly versions = signal<TherapySessionVersion[] | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  constructor() {
    inject(TherapySessionApi)
      .versions(this.data.sessionId)
      .subscribe({
        next: (versions) => this.versions.set(versions),
        error: (error: unknown) =>
          this.errorMessage.set(
            problemDetailMessage(error, 'No se pudieron cargar las versiones. Intente nuevamente.'),
          ),
      });
  }
}

export function openSessionVersionsDialog(
  dialog: MatDialog,
  data: SessionVersionsDialogData,
): Observable<void> {
  return dialog
    .open<SessionVersionsDialog, SessionVersionsDialogData, void>(SessionVersionsDialog, {
      data,
      width: '640px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: 'dialog',
    })
    .afterClosed();
}
