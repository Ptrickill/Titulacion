import { DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { filter, finalize, Observable, switchMap } from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import {
  DATA_REQUEST_STATUS_LABELS,
  DATA_REQUEST_STATUS_TONES,
  DATA_REQUEST_TYPE_LABELS,
  DataRequest,
  isOpenRequest,
} from '../../../shared/legal/privacy-labels';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { PatientPrivacyApi } from './patient-privacy-api';
import { openRegisterRequestDialog, openRequestResponseDialog } from './patient-privacy-dialogs';

/**
 * "Solicitudes del titular" of the patient record (MEJ-06): the requests with their due date and the days since they
 * were received, "Registrar solicitud" and, for an open one, "Marcar en curso", "Resolver" and "Rechazar" with the
 * response. An erasure is accepted only by the operations director, so here it can only go in progress or be rejected.
 */
@Component({
  selector: 'app-data-requests-panel',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressBarModule, StatusBadge],
  template: `
    <div class="toolbar">
      <p class="hint">
        Plazo de respuesta: {{ dueDays() }} días desde la recepción (a confirmar por la clínica).
      </p>
      <button mat-stroked-button type="button" [disabled]="busy()" (click)="register()">
        <mat-icon aria-hidden="true">add</mat-icon>
        Registrar solicitud
      </button>
    </div>
    @if (requests.hasValue()) {
      @let list = requests.value();
      @if (list.length === 0) {
        <p class="empty">El paciente no tiene solicitudes registradas.</p>
      } @else {
        <ul class="requests" aria-label="Solicitudes del titular">
          @for (request of list; track request.id) {
            <li class="request">
              <div class="head">
                <span class="name">{{ typeLabels[request.type] }}</span>
                <span class="badges">
                  @if (request.overdue) {
                    <app-status-badge label="Plazo vencido" tone="danger" />
                  }
                  <app-status-badge
                    [label]="statusLabels[request.status]"
                    [tone]="statusTones[request.status]"
                  />
                </span>
              </div>
              <p class="detail">
                Recibida el {{ request.receivedOn | date: 'dd/MM/yyyy' }} · hace
                {{ request.daysElapsed }} {{ request.daysElapsed === 1 ? 'día' : 'días' }} ·
                responder hasta el
                {{ request.dueOn | date: 'dd/MM/yyyy' }}
              </p>
              @if (request.response) {
                <p class="response">
                  <strong>Respuesta ({{ request.resolvedAt | date: 'dd/MM/yyyy' }}):</strong>
                  {{ request.response }}
                </p>
              }
              @if (isOpen(request)) {
                <div class="actions">
                  @if (request.status === 'RECEIVED') {
                    <button mat-button type="button" [disabled]="busy()" (click)="start(request)">
                      Marcar en curso
                    </button>
                  }
                  @if (request.type !== 'ERASURE') {
                    <button
                      mat-stroked-button
                      type="button"
                      [disabled]="busy()"
                      (click)="close(request, 'RESOLVED')"
                    >
                      Resolver
                    </button>
                  } @else {
                    <span class="note">La acepta la dirección operativa.</span>
                  }
                  <button
                    mat-button
                    type="button"
                    [disabled]="busy()"
                    (click)="close(request, 'REJECTED')"
                  >
                    Rechazar
                  </button>
                </div>
              }
            </li>
          }
        </ul>
      }
    } @else if (requests.error()) {
      <p class="error" role="alert">No se pudieron cargar las solicitudes.</p>
      <button mat-stroked-button type="button" (click)="requests.reload()">Reintentar</button>
    } @else {
      <mat-progress-bar mode="indeterminate" aria-label="Cargando solicitudes" />
    }
  `,
  styles: `
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--re-space-3);
      margin-bottom: var(--re-space-3);
    }
    .hint,
    .empty,
    .note {
      margin: 0;
      color: var(--re-text-muted);
      font-size: 0.9rem;
    }
    .requests {
      display: grid;
      gap: var(--re-space-3);
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .request {
      padding: var(--re-space-3) var(--re-space-4);
      border: 1px solid var(--re-gray-200);
      border-radius: var(--re-radius-md);
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--re-space-2);
    }
    .badges {
      display: flex;
      flex-wrap: wrap;
      gap: var(--re-space-2);
    }
    .name {
      font-weight: 700;
      color: var(--re-navy);
    }
    .detail,
    .response {
      margin: var(--re-space-2) 0 0;
      font-size: 0.9rem;
      overflow-wrap: anywhere;
    }
    .detail {
      color: var(--re-text-muted);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--re-space-2);
      margin-top: var(--re-space-3);
    }
    .error {
      color: var(--re-danger);
    }
  `,
})
export class DataRequestsPanel {
  private readonly api = inject(PatientPrivacyApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly patientId = input.required<number>();
  /** app.compliance.request-response-days of the backend, shown as the term. */
  readonly dueDays = input(15);

  protected readonly typeLabels = DATA_REQUEST_TYPE_LABELS;
  protected readonly statusLabels = DATA_REQUEST_STATUS_LABELS;
  protected readonly statusTones = DATA_REQUEST_STATUS_TONES;
  protected readonly isOpen = isOpenRequest;
  protected readonly busy = signal(false);

  protected readonly requests = rxResource({
    params: () => this.patientId(),
    stream: ({ params }) => this.api.requests(params),
  });

  protected register(): void {
    openRegisterRequestDialog(this.dialog)
      .pipe(
        filter((request) => request !== undefined),
        switchMap((request) =>
          this.run(this.api.registerRequest(this.patientId(), request.type, request.receivedOn)),
        ),
      )
      .subscribe({
        next: () => this.done('Solicitud registrada.'),
        error: (error: unknown) => this.fail(error),
      });
  }

  protected start(request: DataRequest): void {
    this.run(this.api.changeRequest(this.patientId(), request.id, 'IN_PROGRESS', null)).subscribe({
      next: () => this.done('Solicitud en curso.'),
      error: (error: unknown) => this.fail(error),
    });
  }

  protected close(request: DataRequest, status: 'RESOLVED' | 'REJECTED'): void {
    openRequestResponseDialog(this.dialog, { status, typeLabel: this.typeLabels[request.type] })
      .pipe(
        filter((response) => response !== undefined),
        switchMap((response) =>
          this.run(this.api.changeRequest(this.patientId(), request.id, status, response)),
        ),
      )
      .subscribe({
        next: () =>
          this.done(status === 'RESOLVED' ? 'Solicitud resuelta.' : 'Solicitud rechazada.'),
        error: (error: unknown) => this.fail(error),
      });
  }

  private run(request: Observable<DataRequest>): Observable<DataRequest> {
    this.busy.set(true);
    return request.pipe(finalize(() => this.busy.set(false)));
  }

  private done(message: string): void {
    this.requests.reload();
    this.snackBar.open(message, 'Cerrar', { duration: 5000 });
  }

  private fail(error: unknown): void {
    this.snackBar.open(
      problemDetailMessage(error, 'No se pudo guardar la solicitud. Intente nuevamente.'),
      'Cerrar',
      { duration: 6000 },
    );
  }
}
