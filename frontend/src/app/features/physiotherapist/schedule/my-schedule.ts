import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ScheduleApi } from '../../../shared/schedules/schedule-api';
import { WeeklySchedule } from '../../../shared/schedules/weekly-schedule';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';

/**
 * "Mi horario" (MEJ-04): the physiotherapist's own weekly schedule, read only. The administrator and the operations
 * director edit it; the page says so instead of offering an action.
 */
@Component({
  selector: 'app-my-schedule',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    PageHeader,
    EmptyState,
    WeeklySchedule,
  ],
  template: `
    <app-page-header
      title="Mi Horario"
      subtitle="Franjas semanales en las que atiende en la clínica, de lunes a sábado"
    />
    @if (schedule.isLoading()) {
      <mat-progress-bar mode="indeterminate" aria-label="Cargando horario" />
    } @else if (schedule.error()) {
      @if (notFound()) {
        <app-empty-state
          icon="event_busy"
          title="Su cuenta no tiene un registro del personal"
          message="Pida a la administración que revise su registro en el Registro de Usuarios."
        />
      } @else {
        <app-empty-state
          icon="cloud_off"
          title="No se pudo cargar su horario"
          message="Revise su conexión e intente nuevamente."
        >
          <button mat-stroked-button type="button" (click)="schedule.reload()">Reintentar</button>
        </app-empty-state>
      }
    } @else if (schedule.value(); as week) {
      <section class="card" aria-labelledby="my-schedule-title">
        <header class="heading">
          <h2 id="my-schedule-title">Horario semanal</h2>
          <p>Jornada de la clínica: {{ week.openingTime }} a {{ week.closingTime }}</p>
        </header>
        <app-weekly-schedule [slots]="week.slots" />
        <p class="note" role="note">
          <mat-icon aria-hidden="true">info</mat-icon>
          <span>
            Su horario lo registran la administración y la dirección operativa. Si necesita un
            cambio, solicítelo a la dirección operativa.
          </span>
        </p>
      </section>
    }
  `,
  styles: `
    .card {
      max-width: 720px;
      padding: 20px 24px;
      background: var(--re-card);
      border: 1px solid var(--re-gray-200);
      border-radius: var(--re-radius-lg);
      box-shadow: var(--re-shadow-sm);
    }

    .heading {
      margin-bottom: 16px;

      h2 {
        margin: 0;
        color: var(--re-text);
        font-size: 16px;
        font-weight: 700;
      }

      p {
        margin: 4px 0 0;
        color: var(--re-text-muted);
        font-size: 13px;
      }
    }

    .note {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      margin: 16px 0 0;
      padding: 10px 12px;
      color: var(--re-info);
      font-size: 13px;
      background: var(--re-info-bg);
      border: 1px solid var(--re-info-border);
      border-radius: var(--re-radius-sm);

      mat-icon {
        flex-shrink: 0;
        width: 18px;
        height: 18px;
        font-size: 18px;
      }
    }

    @media (max-width: 479.98px) {
      .card {
        padding: 16px;
      }
    }
  `,
})
export class MySchedule {
  private readonly api = inject(ScheduleApi);

  protected readonly schedule = rxResource({ stream: () => this.api.own() });
  protected readonly notFound = computed(() => {
    // A resource may wrap an error that is not an Error instance: the HTTP error is then its cause
    const error = this.schedule.error();
    const http = error instanceof HttpErrorResponse ? error : error?.cause;
    return http instanceof HttpErrorResponse && http.status === 404;
  });
}
