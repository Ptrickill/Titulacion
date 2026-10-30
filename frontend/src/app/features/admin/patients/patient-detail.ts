import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, Observable, of, startWith, switchMap } from 'rxjs';
import { EmptyState } from '../../../shared/ui/empty-state';
import { FormSection } from '../../../shared/ui/form-section';
import { PageHeader } from '../../../shared/ui/page-header';
import { formatAge } from './patient-age';
import { Patient, PatientApi } from './patient-api';
import { SEX_LABELS } from './patient-options';

type DetailState =
  | { status: 'loading' }
  | { status: 'loaded'; patient: Patient }
  | { status: 'notFound' }
  | { status: 'error' };

/**
 * Read-only record of a patient's admission and personal data (HU-05): opened after registering a patient and
 * from the "already registered" notice. Never shows clinical data; HU-06 adds editing.
 */
@Component({
  selector: 'app-patient-detail',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatProgressBarModule,
    PageHeader,
    FormSection,
    EmptyState,
  ],
  templateUrl: './patient-detail.html',
  styleUrl: './patient-detail.scss',
})
export class PatientDetail {
  private readonly api = inject(PatientApi);

  protected readonly sexLabels = SEX_LABELS;
  protected readonly formatAge = formatAge;

  protected readonly state = toSignal(
    inject(ActivatedRoute).paramMap.pipe(
      map((params) => Number(params.get('id'))),
      switchMap((id) => this.load(id)),
    ),
    { initialValue: { status: 'loading' } as DetailState },
  );

  private load(id: number): Observable<DetailState> {
    if (!Number.isInteger(id) || id <= 0) {
      return of({ status: 'notFound' });
    }
    return this.api.findById(id).pipe(
      map((patient): DetailState => ({ status: 'loaded', patient })),
      catchError((error: unknown) =>
        of<DetailState>(
          error instanceof HttpErrorResponse && error.status === 404
            ? { status: 'notFound' }
            : { status: 'error' },
        ),
      ),
      startWith<DetailState>({ status: 'loading' }),
    );
  }
}
