import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { filter, finalize, switchMap } from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import {
  DATA_REQUEST_STATUS_LABELS,
  DATA_REQUEST_STATUS_TONES,
} from '../../../shared/legal/privacy-labels';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { ErasureApi, ErasureRequest } from './erasure-api';
import { openErasureDialog } from './erasure-dialog';

/**
 * `/director/data-requests` (MEJ-06): the open erasure requests that the reception registered, the oldest first, with
 * their due date. Only the operations director accepts one (confirming with the patient's national ID) or rejects it.
 * It shows the patient's name and national ID and nothing else of its data.
 */
@Component({
  selector: 'app-erasure-requests',
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    PageHeader,
    EmptyState,
    StatusBadge,
  ],
  template: `
    <app-page-header
      title="Solicitudes de eliminación"
      subtitle="Solicitudes del titular registradas en recepción que esperan la decisión de la dirección operativa"
    />
    <p class="note">
      <mat-icon aria-hidden="true">info</mat-icon>
      Aceptar una solicitud bloquea al paciente y borra sus datos de contacto; la historia clínica
      se conserva. Para confirmar, se escribe la cédula del paciente.
    </p>
    @if (requests.hasValue()) {
      @let list = requests.value();
      @if (list.length === 0) {
        <app-empty-state
          icon="task_alt"
          title="No hay solicitudes de eliminación pendientes"
          message="Cuando la recepción registre una, aparecerá aquí."
        />
      } @else {
        <ul class="list" aria-label="Solicitudes de eliminación pendientes">
          @for (erasure of list; track erasure.request.id) {
            <li class="item">
              <div class="head">
                <div>
                  <p class="name">{{ erasure.patientName }}</p>
                  <p class="id">Cédula {{ erasure.nationalId }}</p>
                </div>
                <span class="badges">
                  @if (erasure.request.overdue) {
                    <app-status-badge label="Plazo vencido" tone="danger" />
                  }
                  <app-status-badge
                    [label]="statusLabels[erasure.request.status]"
                    [tone]="statusTones[erasure.request.status]"
                  />
                </span>
              </div>
              <p class="detail">
                Recibida el {{ erasure.request.receivedOn | date: 'dd/MM/yyyy' }} · hace
                {{ erasure.request.daysElapsed }}
                {{ erasure.request.daysElapsed === 1 ? 'día' : 'días' }} · responder hasta el
                {{ erasure.request.dueOn | date: 'dd/MM/yyyy' }}
              </p>
              <div class="actions">
                <button
                  mat-flat-button
                  type="button"
                  class="accept"
                  [disabled]="busy()"
                  (click)="decide(erasure, 'accept')"
                >
                  <mat-icon aria-hidden="true">person_remove</mat-icon>
                  Aceptar eliminación
                </button>
                <button
                  mat-button
                  type="button"
                  [disabled]="busy()"
                  (click)="decide(erasure, 'reject')"
                >
                  Rechazar
                </button>
              </div>
            </li>
          }
        </ul>
      }
    } @else if (requests.error()) {
      <app-empty-state
        icon="cloud_off"
        title="No se pudieron cargar las solicitudes"
        message="Revise su conexión e intente nuevamente."
      >
        <button mat-stroked-button type="button" (click)="requests.reload()">Reintentar</button>
      </app-empty-state>
    } @else {
      <mat-progress-bar mode="indeterminate" aria-label="Cargando solicitudes" />
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--re-space-5);
    }
    .note {
      display: flex;
      align-items: flex-start;
      gap: var(--re-space-2);
      margin: 0;
      padding: var(--re-space-3) var(--re-space-4);
      border: 1px solid var(--re-info-border);
      border-radius: var(--re-radius-md);
      background: var(--re-info-bg);
      color: var(--re-info);
      font-size: 0.9rem;
    }
    .note mat-icon {
      flex-shrink: 0;
    }
    .list {
      display: grid;
      gap: var(--re-space-4);
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .item {
      padding: var(--re-space-5);
      border: 1px solid var(--re-gray-200);
      border-radius: var(--re-radius-lg);
      background: var(--re-card);
      box-shadow: var(--re-shadow-sm);
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--re-space-3);
    }
    .badges {
      display: flex;
      flex-wrap: wrap;
      gap: var(--re-space-2);
    }
    .name {
      margin: 0;
      font-weight: 700;
      color: var(--re-navy);
    }
    .id,
    .detail {
      margin: var(--re-space-1) 0 0;
      color: var(--re-text-muted);
      font-size: 0.9rem;
      overflow-wrap: anywhere;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--re-space-2);
      margin-top: var(--re-space-4);
    }
    .accept {
      --mat-button-filled-container-color: var(--re-danger);
      --mat-button-filled-label-text-color: var(--re-card);
    }
  `,
})
export class ErasureRequests {
  private readonly api = inject(ErasureApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly statusLabels = DATA_REQUEST_STATUS_LABELS;
  protected readonly statusTones = DATA_REQUEST_STATUS_TONES;
  protected readonly busy = signal(false);
  protected readonly requests = rxResource({ stream: () => this.api.openRequests() });

  protected decide(erasure: ErasureRequest, decision: 'accept' | 'reject'): void {
    openErasureDialog(this.dialog, { erasure, decision })
      .pipe(
        filter((result) => result !== undefined),
        switchMap((result) => {
          this.busy.set(true);
          const request =
            result.decision === 'accept'
              ? this.api.accept(erasure.request.id, result.nationalId, result.response)
              : this.api.reject(erasure.request.id, result.response);
          return request.pipe(finalize(() => this.busy.set(false)));
        }),
      )
      .subscribe({
        next: (closed) => {
          this.requests.reload();
          this.snackBar.open(
            closed.status === 'RESOLVED'
              ? `Se eliminaron los datos de contacto de ${erasure.patientName}.`
              : 'Solicitud rechazada.',
            'Cerrar',
            { duration: 6000 },
          );
        },
        error: (error: unknown) =>
          this.snackBar.open(
            problemDetailMessage(error, 'No se pudo resolver la solicitud. Intente nuevamente.'),
            'Cerrar',
            { duration: 6000 },
          ),
      });
  }
}
