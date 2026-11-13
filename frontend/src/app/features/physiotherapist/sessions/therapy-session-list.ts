import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth-service';
import { Page } from '../../../core/http/page';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { DataTable, DataTableColumn } from '../../../shared/ui/data-table/data-table';
import { DataTableCell } from '../../../shared/ui/data-table/data-table-cell';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatusBadge } from '../../../shared/ui/status-badge';
import {
  painScoreTone,
  TREATMENT_STATUS_LABELS,
  TREATMENT_STATUS_TONES,
} from '../clinical-records/clinical-labels';
import { Treatment } from '../clinical-records/clinical-record-api';
import { TreatmentApi } from '../clinical-records/treatment-api';
import { openSessionVersionsDialog } from './session-versions-dialog';
import {
  PROGRESS_STATUS_LABELS,
  PROGRESS_STATUS_TONES,
  SESSION_STATUS_LABELS,
  SESSION_STATUS_TONES,
} from './session-labels';
import { TherapySession, TherapySessionApi } from './therapy-session-api';

const PAGE_SIZE = 10;

/**
 * `/physio/patients/:patientId/treatments/:treatmentId/sessions` (HU-10): the sessions of a treatment ordered by
 * number, paginated in the server, with their EVA, status, progress and responsible. "Corregir" is offered only on
 * the sessions the authenticated physiotherapist registered (the server enforces it too); "Ver versiones" on all.
 * "Registrar sesión" appears while the treatment is active.
 */
@Component({
  selector: 'app-therapy-session-list',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    PageHeader,
    DataTable,
    DataTableCell,
    EmptyState,
    StatusBadge,
  ],
  templateUrl: './therapy-session-list.html',
  styleUrl: './therapy-session-list.scss',
})
export class TherapySessionList {
  private readonly api = inject(TherapySessionApi);
  private readonly dialog = inject(MatDialog);
  private readonly auth = inject(AuthService);

  private readonly params = inject(ActivatedRoute).snapshot.paramMap;
  private readonly patientId = Number(this.params.get('patientId'));
  private readonly treatmentId = Number(this.params.get('treatmentId'));
  protected readonly recordView = `/physio/patients/${this.patientId}/clinical-record`;
  protected readonly sessionsBase = `/physio/patients/${this.patientId}/treatments/${this.treatmentId}/sessions`;
  protected readonly timelineView = `/physio/patients/${this.patientId}/treatments/${this.treatmentId}/timeline`;

  protected readonly pageSize = PAGE_SIZE;
  protected readonly painTone = painScoreTone;
  protected readonly statusLabels = SESSION_STATUS_LABELS;
  protected readonly statusTones = SESSION_STATUS_TONES;
  protected readonly progressLabels = PROGRESS_STATUS_LABELS;
  protected readonly progressTones = PROGRESS_STATUS_TONES;
  protected readonly treatmentStatusLabels = TREATMENT_STATUS_LABELS;
  protected readonly treatmentStatusTones = TREATMENT_STATUS_TONES;

  /** Every column has its own cell template; the keys only name them. */
  protected readonly columns: DataTableColumn<TherapySession>[] = [
    { key: 'sequenceNumber', header: 'N.º' },
    { key: 'sessionDate', header: 'Fecha' },
    { key: 'sessionType', header: 'Tipo de sesión' },
    { key: 'painScore', header: 'EVA', align: 'center' },
    { key: 'status', header: 'Estado y evolución' },
    { key: 'responsible', header: 'Responsable' },
    { key: 'id', header: 'Acciones', align: 'end' },
  ];

  protected readonly pageIndex = signal(0);
  protected readonly sessions = signal<Page<TherapySession> | null>(null);
  protected readonly treatment = signal<Treatment | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  constructor() {
    inject(TreatmentApi)
      .find(this.treatmentId)
      .subscribe({
        next: (treatment) => {
          if (treatment.patientId !== this.patientId) {
            this.loadError.set('El tratamiento no pertenece a este paciente.');
            return;
          }
          this.treatment.set(treatment);
        },
        error: (error: unknown) => this.loadError.set(this.messageFor(error)),
      });

    toObservable(this.pageIndex)
      .pipe(
        switchMap((page) => {
          this.loading.set(true);
          return this.api.list(this.treatmentId, page, PAGE_SIZE).pipe(
            catchError((error: unknown) => {
              this.loadError.set(this.messageFor(error));
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((page) => {
        this.loading.set(false);
        if (page) {
          this.sessions.set(page);
        }
      });
  }

  /** Only the physiotherapist who registered a session may correct it. */
  protected isMine(session: TherapySession): boolean {
    return session.responsible.id === this.auth.user()?.id;
  }

  protected showVersions(session: TherapySession): void {
    openSessionVersionsDialog(this.dialog, {
      sessionId: session.id,
      sequenceNumber: session.sequenceNumber,
    }).subscribe();
  }

  private messageFor(error: unknown): string {
    return error instanceof HttpErrorResponse && error.status === 404
      ? problemDetailMessage(error, 'El tratamiento no existe.')
      : 'Revise su conexión e intente nuevamente.';
  }
}
