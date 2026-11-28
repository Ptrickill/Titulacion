import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  catchError,
  filter,
  finalize,
  map,
  merge,
  Observable,
  of,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';
import { FileDownload } from '../../../core/http/file-download';
import { blobProblemDetailMessage, problemDetailMessage } from '../../../core/http/problem-detail';
import { openConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { EmptyState } from '../../../shared/ui/empty-state';
import { FormSection } from '../../../shared/ui/form-section';
import { PageHeader } from '../../../shared/ui/page-header';
import { AccountActions } from '../../../shared/people/account-actions';
import { PersonAccountCard } from '../../../shared/people/person-account-card';
import { openPersonFormDialog } from '../../../shared/people/person-form-dialog';
import { formatAge, todayIsoDate } from '../../../shared/people/patient-age';
import { Patient, PatientApi } from '../../../shared/people/patient-api';
import { SEX_LABELS } from '../../../shared/people/patient-options';
import { DataRequestsPanel } from './data-requests-panel';
import { PatientConsentsPanel } from './patient-consents-panel';
import { ExportFormat, PatientPrivacyApi } from './patient-privacy-api';

type DetailState =
  | { status: 'loading' }
  | { status: 'loaded'; patient: Patient }
  | { status: 'notFound' }
  | { status: 'error' };

/**
 * Record of a patient's admission and personal data (HU-05): opened from the reception's patient registry (MEJ-05), after registering a
 * patient and from the "already registered" notice. "Editar datos" opens the form in edit mode (HU-06), and
 * "Acceso al portal" (MEJ-02) creates the patient's account with its email and names, or shows its status and resends
 * the pending link. Never shows clinical data. MEJ-06: the consents, the requests of the data subject and "Exportar datos"
 * (PDF or JSON with the personal data, the consents and the requests); a patient blocked by an accepted erasure is
 * read-only.
 */
@Component({
  selector: 'app-patient-detail',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    PageHeader,
    PatientConsentsPanel,
    DataRequestsPanel,
    FormSection,
    EmptyState,
    PersonAccountCard,
  ],
  templateUrl: './patient-detail.html',
  styleUrl: './patient-detail.scss',
})
export class PatientDetail {
  private readonly api = inject(PatientApi);
  private readonly accounts = inject(AccountActions);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly privacy = inject(PatientPrivacyApi);
  private readonly fileDownload = inject(FileDownload);
  /** Data saved here replaces the shown record without loading it again. */
  private readonly saved = new Subject<Patient>();

  protected readonly sexLabels = SEX_LABELS;
  protected readonly formatAge = formatAge;
  /** The portal account is being created or its link resent. */
  protected readonly accountBusy = signal(false);
  /** The export of the personal data is being downloaded. */
  protected readonly exporting = signal(false);

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
    openPersonFormDialog(this.dialog, { patient }).subscribe((result) => {
      if (result?.type === 'PATIENT') {
        this.saved.next(result.patient);
        this.snackBar.open('Datos del paciente actualizados correctamente.', 'Cerrar', {
          duration: 5000,
        });
      }
    });
  }

  /** A consent changed (MEJ-06): the pending consent and the portal account may have changed too. */
  protected refresh(patient: Patient): void {
    this.api.findById(patient.id).subscribe((updated) => this.saved.next(updated));
  }

  /** "Exportar datos" (MEJ-06): the file the reception hands to the data subject; audited as EXPORT. */
  protected exportData(patient: Patient, format: ExportFormat): void {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    this.privacy
      .exportData(patient.id, format)
      .pipe(finalize(() => this.exporting.set(false)))
      .subscribe({
        next: (file) =>
          this.fileDownload.save(file, `datos-paciente-${patient.id}-${todayIsoDate()}.${format}`),
        error: (error: unknown) =>
          void blobProblemDetailMessage(
            error,
            'No se pudo exportar los datos del paciente. Intente nuevamente.',
          ).then((message) => this.snackBar.open(message, 'Cerrar', { duration: 6000 })),
      });
  }

  /** "Crear acceso al portal": after confirming, the account takes the record's email and names. */
  protected createPortalAccount(patient: Patient): void {
    if (this.accountBusy()) {
      return;
    }
    openConfirmDialog(this.dialog, {
      title: 'Crear acceso al portal',
      message: `Se creará la cuenta de ${patient.firstName} ${patient.lastName} y se enviará el enlace de activación a ${patient.email}.`,
      confirmLabel: 'Crear acceso',
      icon: 'person_add',
    })
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.accountBusy.set(true);
          return this.api
            .createPortalAccount(patient.id)
            .pipe(finalize(() => this.accountBusy.set(false)));
        }),
      )
      .subscribe({
        next: (account) => {
          this.saved.next({ ...patient, portalAccount: account });
          this.snackBar.open(`Enviamos un enlace de activación a ${patient.email}`, 'Cerrar', {
            duration: 6000,
          });
        },
        error: (error: unknown) =>
          this.snackBar.open(
            problemDetailMessage(
              error,
              'No se pudo crear el acceso al portal. Intente nuevamente.',
            ),
            'Cerrar',
            { duration: 6000 },
          ),
      });
  }

  /** Resends the activation link, or the confirmation of a new email. */
  protected resend(patient: Patient): void {
    const account = patient.portalAccount;
    if (!account || this.accountBusy()) {
      return;
    }
    this.accountBusy.set(true);
    this.accounts
      .resendOf(account, patient.email ?? '')
      .pipe(finalize(() => this.accountBusy.set(false)))
      .subscribe((updated) => this.saved.next({ ...patient, portalAccount: updated }));
  }

  /** Activates or deactivates the portal account, after a confirmation. */
  protected toggleStatus(patient: Patient): void {
    const account = patient.portalAccount;
    if (!account || this.accountBusy()) {
      return;
    }
    this.accountBusy.set(true);
    this.accounts
      .toggleStatusOf(account, `${patient.firstName} ${patient.lastName}`)
      .pipe(finalize(() => this.accountBusy.set(false)))
      .subscribe((updated) => this.saved.next({ ...patient, portalAccount: updated }));
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
