import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { catchError, filter, forkJoin, of, switchMap, tap } from 'rxjs';
import { AuthService } from '../../core/auth/auth-service';
import { problemDetailMessage } from '../../core/http/problem-detail';
import { TREATMENT_STATUS_LABELS } from '../../shared/clinical/treatment-labels';
import { EmptyState } from '../../shared/ui/empty-state';
import { HeroBanner } from '../../shared/ui/hero-banner';
import { PainEvolutionCard } from './pain-evolution-card';
import {
  PortalApi,
  PortalInstruction,
  PortalSession,
  PortalTreatment,
  TreatmentProgress,
} from './portal-api';
import { PortalSessionList } from './portal-session-list';
import { TherapistInstructionCard } from './therapist-instruction-card';
import { TreatmentProgressCard } from './treatment-progress-card';

/** What the portal shows of the treatment chosen: it arrives together, from three requests in parallel. */
interface TreatmentDetail {
  progress: TreatmentProgress;
  sessions: PortalSession[];
  /** Null when no session has instructions for the patient. */
  instruction: PortalInstruction | null;
}

const WELCOME =
  'Aquí puedes consultar el progreso de tu tratamiento, tus sesiones y las indicaciones de tu fisioterapeuta.';

/**
 * `/patient` (HU-12 and HU-13, prototype 14): the portal of the patient. A banner with the greeting and the
 * diagnosis; three cards with the progress of the treatment (sessions that took place against the planned ones), the
 * evolution of the pain and the last instructions of the physiotherapist; and the list of sessions. With more than
 * one treatment, a selector shows the active one by default. Everything is read only: the page has no action that
 * changes data. The server takes the patient from the session and audits each read.
 */
@Component({
  selector: 'app-patient-dashboard',
  imports: [
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressBarModule,
    MatSelectModule,
    HeroBanner,
    EmptyState,
    TreatmentProgressCard,
    PainEvolutionCard,
    TherapistInstructionCard,
    PortalSessionList,
  ],
  templateUrl: './patient-dashboard.html',
  styleUrl: './patient-dashboard.scss',
})
export class PatientDashboard {
  private readonly auth = inject(AuthService);
  private readonly api = inject(PortalApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly statusLabels = TREATMENT_STATUS_LABELS;

  /** Null until the list arrives. */
  protected readonly treatments = signal<PortalTreatment[] | null>(null);
  /** The treatment whose detail is asked for; a new object on every request, so a retry asks again. */
  private readonly request = signal<{ treatmentId: number } | null>(null);
  protected readonly selectedId = computed(() => this.request()?.treatmentId ?? null);
  protected readonly detail = signal<TreatmentDetail | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  /** The account has no linked patient (404): there is nothing to show, and retrying does not help. */
  protected readonly unlinked = signal(false);

  protected readonly selected = computed(
    () => this.treatments()?.find((treatment) => treatment.id === this.selectedId()) ?? null,
  );

  protected readonly greeting = computed(() => {
    const firstName = this.auth.user()?.fullName.split(/\s+/)[0];
    return firstName ? `¡Hola, ${firstName}!` : '¡Hola!';
  });

  /** The diagnosis and who is in charge; for a treatment that is not active, how it ended. */
  protected readonly bannerMessage = computed(() => {
    const treatment = this.selected();
    if (!treatment) {
      return WELCOME;
    }
    const subject = `Tu tratamiento para ${treatment.diagnosis.description}`;
    switch (treatment.status) {
      case 'ACTIVE':
        return treatment.responsibleName
          ? `${subject} está a cargo de ${treatment.responsibleName}.`
          : `${subject} está activo.`;
      case 'COMPLETED':
        return `${subject} finalizó.`;
      case 'ABANDONED':
        return `${subject} figura como abandonado.`;
    }
  });

  constructor() {
    this.loadTreatments();

    toObservable(this.request)
      .pipe(
        filter((request) => request !== null),
        tap(() => {
          this.loading.set(true);
          this.loadError.set(null);
        }),
        switchMap(({ treatmentId }) =>
          forkJoin({
            progress: this.api.progress(treatmentId),
            sessions: this.api.sessions(treatmentId),
            instruction: this.api.instructions(treatmentId),
          }).pipe(
            catchError((error: HttpErrorResponse) => {
              this.loadError.set(problemDetailMessage(error, 'No se pudo cargar tu tratamiento.'));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((detail) => {
        this.detail.set(detail);
        this.loading.set(false);
      });
  }

  /** Shows the treatment chosen in the selector. */
  protected select(treatmentId: number): void {
    this.request.set({ treatmentId });
  }

  /** Asks again for what failed: the list of treatments or the detail of the selected one. */
  protected retry(): void {
    const treatmentId = this.selectedId();
    if (treatmentId === null) {
      this.loadTreatments();
    } else {
      this.request.set({ treatmentId });
    }
  }

  private loadTreatments(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api
      .treatments()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (treatments) => {
          this.treatments.set(treatments);
          if (treatments.length === 0) {
            this.loading.set(false);
          } else {
            // The server sends the active treatment first
            this.request.set({ treatmentId: treatments[0].id });
          }
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 404) {
            this.unlinked.set(true);
          } else {
            this.loadError.set(problemDetailMessage(error, 'No se pudo cargar tu tratamiento.'));
          }
          this.loading.set(false);
        },
      });
  }
}
