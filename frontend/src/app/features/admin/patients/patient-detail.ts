import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, merge, Observable, of, startWith, Subject, switchMap } from 'rxjs';
import { EmptyState } from '../../../shared/ui/empty-state';
import { FormSection } from '../../../shared/ui/form-section';
import { PageHeader } from '../../../shared/ui/page-header';
import { formatAge } from './patient-age';
import { Patient, PatientApi } from './patient-api';
import { openPatientFormDialog } from './patient-form-dialog';
import { SEX_LABELS } from './patient-options';

type DetailState =
  | { status: 'loading' }
  | { status: 'loaded'; patient: Patient }
  | { status: 'notFound' }
  | { status: 'error' };

/**
 * Record of a patient's admission and personal data (HU-05): opened after registering a patient and from the
 * "already registered" notice. "Editar datos" opens the patient form in edit mode (HU-06). Never shows clinical data.
 */
@Component({
  selector: 'app-patient-detail',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
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
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  /** Data saved in the edit form replaces the shown record without loading it again. */
  private readonly saved = new Subject<Patient>();

  protected readonly sexLabels = SEX_LABELS;
  protected readonly formatAge = formatAge;

  protected readonly state = toSignal(
    merge(
      inject(ActivatedRoute).paramMap.pipe(
        map((params) => Number(params.get('id'))),
        switchMap((id) => this.load(id)),
      ),
      this.saved.pipe(map((patient): DetailState => ({ status: 'loaded', patient }))),
    ),
    { initialValue: { status: 'loading' } as DetailState },
  );

  protected edit(patient: Patient): void {
    openPatientFormDialog(this.dialog, patient).subscribe((saved) => {
      if (saved) {
        this.saved.next(saved);
        this.snackBar.open('Datos del paciente actualizados correctamente.', 'Cerrar', {
          duration: 5000,
        });
      }
    });
  }

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
