import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, Observable, of, switchMap } from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { EmptyState } from '../../../shared/ui/empty-state';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { Patient, PatientApi } from '../../admin/patients/patient-api';
import { painScoreTone } from '../clinical-records/clinical-labels';
import { PainCurveChart } from './pain-curve-chart';
import {
  PROGRESS_STATUS_LABELS,
  PROGRESS_STATUS_TONES,
  SESSION_STATUS_LABELS,
  SESSION_STATUS_TONES,
} from './session-labels';
import {
  TherapySessionApi,
  TherapyTimeline as Timeline,
  TimelineRange,
} from './therapy-session-api';

const NO_RANGE: TimelineRange = { from: null, to: null };

/** "Desde" after "Hasta" is an error of the whole form; the dates are yyyy-MM-dd, so they compare as text. */
function dateRange(group: AbstractControl): ValidationErrors | null {
  const { from, to } = (group as FormGroup).getRawValue() as TimelineRange;
  return from && to && from > to ? { range: true } : null;
}

/**
 * `/physio/patients/:patientId/treatments/:treatmentId/timeline` (HU-11, prototype 05): the sessions of a treatment
 * in chronological order, each with its EVA, status, progress, activities, notes and responsible, so a
 * physiotherapist understands the evolution when someone else attended the patient. Above them, the pain curve
 * (initial evaluation and completed sessions) and a date-range filter; every change reloads, and an inverted range
 * only shows a notice. The server audits each read as READ of the treatment.
 */
@Component({
  selector: 'app-therapy-timeline',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    PageHeader,
    EmptyState,
    StatusBadge,
    PainCurveChart,
  ],
  templateUrl: './therapy-timeline.html',
  styleUrl: './therapy-timeline.scss',
})
export class TherapyTimeline {
  private readonly api = inject(TherapySessionApi);

  private readonly params = inject(ActivatedRoute).snapshot.paramMap;
  private readonly patientId = Number(this.params.get('patientId'));
  private readonly treatmentId = Number(this.params.get('treatmentId'));
  protected readonly recordView = `/physio/patients/${this.patientId}/clinical-record`;
  protected readonly sessionsBase = `/physio/patients/${this.patientId}/treatments/${this.treatmentId}/sessions`;

  protected readonly painTone = painScoreTone;
  protected readonly statusLabels = SESSION_STATUS_LABELS;
  protected readonly statusTones = SESSION_STATUS_TONES;
  protected readonly progressLabels = PROGRESS_STATUS_LABELS;
  protected readonly progressTones = PROGRESS_STATUS_TONES;

  protected readonly filters = new FormGroup(
    {
      from: new FormControl<string | null>(null),
      to: new FormControl<string | null>(null),
    },
    { validators: dateRange },
  );

  private readonly range = signal<TimelineRange>(NO_RANGE);
  protected readonly filtered = computed(() => !!(this.range().from || this.range().to));
  protected readonly timeline = signal<Timeline | null>(null);
  protected readonly patient = signal<Patient | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  constructor() {
    const destroyRef = inject(DestroyRef);
    // Personal data only (not audited); without it the bar shows the patient id
    inject(PatientApi)
      .findById(this.patientId)
      .subscribe({ next: (patient) => this.patient.set(patient), error: () => undefined });

    this.filters.valueChanges.pipe(takeUntilDestroyed(destroyRef)).subscribe(() => {
      if (this.filters.valid) {
        const { from, to } = this.filters.getRawValue();
        this.range.set({ from: from || null, to: to || null });
      }
    });

    toObservable(this.range)
      .pipe(
        switchMap((range) => this.load(range)),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (typeof result === 'string') {
          this.loadError.set(result);
          return;
        }
        this.timeline.set(result);
      });
  }

  protected clear(): void {
    this.filters.reset();
  }

  /** The timeline, or the message of the error. */
  private load(range: TimelineRange): Observable<Timeline | string> {
    this.loading.set(true);
    return this.api.timeline(this.treatmentId, range).pipe(
      map((timeline): Timeline | string =>
        timeline.treatment.patientId === this.patientId
          ? timeline
          : 'El tratamiento no pertenece a este paciente.',
      ),
      catchError((error: unknown) =>
        of(
          error instanceof HttpErrorResponse && (error.status === 404 || error.status === 400)
            ? problemDetailMessage(error, 'El tratamiento no existe.')
            : 'Revise su conexión e intente nuevamente.',
        ),
      ),
    );
  }
}
