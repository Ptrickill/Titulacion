import { TextFieldModule } from '@angular/cdk/text-field';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, map, Observable, of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth-service';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatusBadge } from '../../../shared/ui/status-badge';
// GET /api/patients/{id} is shared by the administrator and the physiotherapist (personal data only)
import { Patient, PatientApi } from '../../admin/patients/patient-api';
import { painScoreTone } from '../clinical-records/clinical-labels';
import { Treatment } from '../clinical-records/clinical-record-api';
import { TreatmentApi } from '../clinical-records/treatment-api';
import {
  PAIN_SCORES,
  painScoreLabel,
  PROGRESS_STATUS_LABELS,
  PROGRESS_STATUSES,
  SESSION_STATUS_OPTIONS,
  SESSION_STATUSES,
} from './session-labels';
import {
  ProgressStatus,
  SESSION_TEXT_MAX,
  SESSION_TYPE_MAX,
  SessionStatus,
  TherapySession,
  TherapySessionApi,
  TherapySessionRequest,
} from './therapy-session-api';

export const ONLY_THE_AUTHOR_CORRECTS =
  'Solo el fisioterapeuta que registró la sesión puede corregirla.';
const NOT_ACTIVE = 'El tratamiento no está activo: no se pueden registrar sesiones nuevas.';
const CHECK_THE_FIELDS = 'Revise los campos marcados antes de guardar.';

/** Today in the browser's time zone (the clinic's), as `yyyy-MM-dd`. */
function localDate(date = new Date()): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function localTime(date = new Date()): string {
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

/** The server rejects a session dated after today (400); the form says it first. */
function notInTheFuture(control: AbstractControl<string>): ValidationErrors | null {
  return control.value && control.value > localDate() ? { future: true } : null;
}

/** Text fields must have something besides spaces, as the backend's @NotBlank. */
function notBlank(control: AbstractControl<string>): ValidationErrors | null {
  return control.value && !control.value.trim() ? { required: true } : null;
}

/**
 * Therapy session form of the physiotherapist (HU-10, prototype 06).
 *
 * - `/physio/patients/:patientId/treatments/:treatmentId/sessions/new` registers a session on the active treatment.
 * - `/physio/patients/:patientId/treatments/:treatmentId/sessions/:sessionId/edit` corrects it; only the
 *   physiotherapist who registered it may do so, the others see the form read-only.
 *
 * The session number and the responsible are read-only: the server assigns them (the number shown when registering
 * is the next one, confirmed on saving). After saving, the form opens the session list of the treatment.
 */
@Component({
  selector: 'app-therapy-session-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TextFieldModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    PageHeader,
    EmptyState,
    StatusBadge,
  ],
  templateUrl: './therapy-session-form.html',
  styleUrl: './therapy-session-form.scss',
})
export class TherapySessionForm {
  private readonly api = inject(TherapySessionApi);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  private readonly params = inject(ActivatedRoute).snapshot.paramMap;
  private readonly patientId = Number(this.params.get('patientId'));
  private readonly treatmentId = Number(this.params.get('treatmentId'));
  private readonly sessionId = this.params.has('sessionId')
    ? Number(this.params.get('sessionId'))
    : null;
  protected readonly isEdit = this.sessionId !== null;
  /** The session list of the treatment, where the form returns after saving. */
  private readonly sessionList = `/physio/patients/${this.patientId}/treatments/${this.treatmentId}/sessions`;
  protected readonly backLink = this.isEdit
    ? this.sessionList
    : `/physio/patients/${this.patientId}/clinical-record`;

  protected readonly today = localDate();
  protected readonly textMax = SESSION_TEXT_MAX;
  protected readonly typeMax = SESSION_TYPE_MAX;
  protected readonly painScores = PAIN_SCORES;
  protected readonly painLabel = painScoreLabel;
  protected readonly painTone = painScoreTone;
  protected readonly statuses = SESSION_STATUSES;
  protected readonly statusOptions = SESSION_STATUS_OPTIONS;
  protected readonly progressStatuses = PROGRESS_STATUSES;
  protected readonly progressLabels = PROGRESS_STATUS_LABELS;

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly text = [Validators.required, notBlank, Validators.maxLength(SESSION_TEXT_MAX)];

  protected readonly form = this.fb.group({
    sessionType: ['', [Validators.required, notBlank, Validators.maxLength(SESSION_TYPE_MAX)]],
    sessionDate: [this.today, [Validators.required, notInTheFuture]],
    sessionTime: [localTime(), Validators.required],
    painScore: [null as number | null, Validators.required],
    status: ['COMPLETED' as SessionStatus | null, Validators.required],
    progressStatus: [null as ProgressStatus | null, Validators.required],
    activitiesPerformed: ['', this.text],
    clinicalObservations: ['', this.text],
    patientInstructions: ['', Validators.maxLength(SESSION_TEXT_MAX)],
  });

  private readonly patient = signal<Patient | null>(null);
  private readonly treatment = signal<Treatment | null>(null);
  private readonly session = signal<TherapySession | null>(null);
  /** Next session number of the treatment (registration only). */
  private readonly nextSequence = signal<number | null>(null);

  protected readonly ready = computed(() => this.patient() !== null && this.treatment() !== null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly patientLabel = computed(() => {
    const patient = this.patient();
    const treatment = this.treatment();
    return patient && treatment
      ? `${patient.firstName} ${patient.lastName} (${patient.nationalId}) - ${treatment.diagnosis.description}`
      : '';
  });
  protected readonly sequenceLabel = computed(() => {
    const number = this.session()?.sequenceNumber ?? this.nextSequence();
    return number === null ? '' : `Sesión N.º ${number}`;
  });
  protected readonly responsibleName = computed(() => {
    const session = this.session();
    if (session) {
      return session.responsible.fullName ?? `Usuario #${session.responsible.id}`;
    }
    return this.auth.user()?.fullName ?? '';
  });
  /** Why the form cannot be saved: a closed treatment, or a session registered by someone else. */
  protected readonly blockedMessage = computed(() => {
    const session = this.session();
    if (session) {
      return session.responsible.id === this.auth.user()?.id ? null : ONLY_THE_AUTHOR_CORRECTS;
    }
    return this.treatment() && this.treatment()!.status !== 'ACTIVE' ? NOT_ACTIVE : null;
  });

  constructor() {
    // The notice about the marked fields goes away once they are fixed
    this.form.statusChanges.pipe(takeUntilDestroyed()).subscribe((status) => {
      if (status === 'VALID' && this.errorMessage() === CHECK_THE_FIELDS) {
        this.errorMessage.set(null);
      }
    });
    forkJoin({
      patient: inject(PatientApi).findById(this.patientId),
      treatment: inject(TreatmentApi).find(this.treatmentId),
      session: this.sessionId === null ? of(null) : this.api.find(this.sessionId),
      // The sessions are never deleted, so the next number is their count + 1
      next: this.sessionId === null ? this.nextNumber() : of(null),
    }).subscribe({
      next: ({ patient, treatment, session, next }) => {
        const mismatch = this.mismatch(treatment, session);
        if (mismatch) {
          this.loadError.set(mismatch);
          return;
        }
        if (session) {
          this.form.setValue({
            sessionType: session.sessionType,
            sessionDate: session.sessionDate,
            sessionTime: session.sessionTime.slice(0, 5),
            painScore: session.painScore,
            status: session.status,
            progressStatus: session.progressStatus,
            activitiesPerformed: session.activitiesPerformed,
            clinicalObservations: session.clinicalObservations,
            patientInstructions: session.patientInstructions ?? '',
          });
        }
        this.session.set(session);
        this.nextSequence.set(next);
        this.patient.set(patient);
        this.treatment.set(treatment);
        if (this.blockedMessage()) {
          this.form.disable();
        }
      },
      error: (error: unknown) => this.loadError.set(this.messageFor(error)),
    });
  }

  protected submit(): void {
    if (this.blockedMessage() || this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage.set(CHECK_THE_FIELDS);
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const request = this.request();
    const save =
      this.sessionId === null
        ? this.api.register(this.treatmentId, request)
        : this.api.correct(this.sessionId, request);
    save.subscribe({
      next: (saved) => {
        this.snackBar.open(
          this.isEdit
            ? `Sesión N.º ${saved.sequenceNumber} corregida correctamente.`
            : `Sesión N.º ${saved.sequenceNumber} registrada correctamente.`,
          'Cerrar',
          { duration: 5000 },
        );
        void this.router.navigateByUrl(this.sessionList);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.errorMessage.set(
          problemDetailMessage(error, 'No se pudo guardar. Revise los datos e intente nuevamente.'),
        );
      },
    });
  }

  private nextNumber(): Observable<number> {
    return this.api.list(this.treatmentId, 0, 1).pipe(map((page) => page.totalElements + 1));
  }

  private mismatch(treatment: Treatment, session: TherapySession | null): string | null {
    if (treatment.patientId !== this.patientId) {
      return 'El tratamiento no pertenece a este paciente.';
    }
    return session && session.treatmentId !== this.treatmentId
      ? 'La sesión no pertenece a este tratamiento.'
      : null;
  }

  private request(): TherapySessionRequest {
    const value = this.form.getRawValue();
    const instructions = value.patientInstructions.trim();
    return {
      sessionDate: value.sessionDate,
      sessionTime: value.sessionTime,
      sessionType: value.sessionType.trim(),
      activitiesPerformed: value.activitiesPerformed.trim(),
      clinicalObservations: value.clinicalObservations.trim(),
      patientInstructions: instructions || null,
      painScore: value.painScore!,
      progressStatus: value.progressStatus!,
      status: value.status!,
    };
  }

  private messageFor(error: unknown): string {
    return error instanceof HttpErrorResponse && error.status === 404
      ? problemDetailMessage(error, 'La sesión o el tratamiento no existe.')
      : 'Revise su conexión e intente nuevamente.';
  }
}
