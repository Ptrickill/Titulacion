import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  catchError,
  finalize,
  map,
  merge,
  Observable,
  of,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';
import { problemDetailMessage } from '../../../core/http/problem-detail';
import { EmptyState } from '../../../shared/ui/empty-state';
import { FormSection } from '../../../shared/ui/form-section';
import { PageHeader } from '../../../shared/ui/page-header';
import { StatusBadge } from '../../../shared/ui/status-badge';
import { formatAge } from '../patients/patient-age';
import { SEX_LABELS } from '../patients/patient-options';
import { UserApi } from '../users/user-api';
import { PERSON_ROLE_LABELS, PERSON_ROLE_TONES } from './people-labels';
import { PersonAccountCard } from './person-account-card';
import { openPersonFormDialog } from './person-form-dialog';
import { Staff, StaffApi } from './staff-api';

type DetailState =
  | { status: 'loading' }
  | { status: 'loaded'; staff: Staff }
  | { status: 'notFound' }
  | { status: 'error' };

/**
 * Record of a staff member (MEJ-02): its personal data, its role and its account. "Editar datos" opens the form in
 * edit mode; a record of an account that existed before MEJ-02 shows "Datos por completar" until it is edited.
 */
@Component({
  selector: 'app-staff-detail',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    PageHeader,
    FormSection,
    EmptyState,
    StatusBadge,
    PersonAccountCard,
  ],
  templateUrl: './staff-detail.html',
  styleUrl: '../patients/patient-detail.scss',
})
export class StaffDetail {
  private readonly api = inject(StaffApi);
  private readonly userApi = inject(UserApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly saved = new Subject<Staff>();

  protected readonly sexLabels = SEX_LABELS;
  protected readonly roleLabels = PERSON_ROLE_LABELS;
  protected readonly roleTones = PERSON_ROLE_TONES;
  protected readonly formatAge = formatAge;
  protected readonly accountBusy = signal(false);

  protected readonly state = toSignal(
    merge(
      inject(ActivatedRoute).paramMap.pipe(
        map((params) => Number(params.get('id'))),
        switchMap((id) => this.load(id)),
      ),
      this.saved.pipe(map((staff): DetailState => ({ status: 'loaded', staff }))),
    ),
    { initialValue: { status: 'loading' } as DetailState },
  );

  protected edit(staff: Staff): void {
    openPersonFormDialog(this.dialog, { staff }).subscribe((result) => {
      if (result?.type === 'STAFF') {
        this.saved.next(result.staff);
        this.snackBar.open('Datos del personal actualizados correctamente.', 'Cerrar', {
          duration: 5000,
        });
      }
    });
  }

  /** Resends the activation link, or the confirmation of a new email. */
  protected resend(staff: Staff): void {
    const account = staff.account;
    if (!account || this.accountBusy()) {
      return;
    }
    this.accountBusy.set(true);
    this.userApi
      .resendInvitation(account.userId)
      .pipe(finalize(() => this.accountBusy.set(false)))
      .subscribe({
        next: (updated) => {
          this.saved.next({
            ...staff,
            account: {
              userId: updated.id,
              status: updated.status,
              emailChangePending: updated.emailChangePending,
            },
          });
          this.snackBar.open(
            updated.emailChangePending
              ? `Enviamos un nuevo enlace de confirmación a ${staff.email}`
              : `Enviamos un nuevo enlace de activación a ${staff.email}`,
            'Cerrar',
            { duration: 6000 },
          );
        },
        error: (error: unknown) =>
          this.snackBar.open(
            problemDetailMessage(error, 'No se pudo reenviar el enlace. Intente nuevamente.'),
            'Cerrar',
            { duration: 6000 },
          ),
      });
  }

  private load(id: number): Observable<DetailState> {
    if (!Number.isInteger(id) || id <= 0) {
      return of({ status: 'notFound' });
    }
    return this.api.findById(id).pipe(
      map((staff): DetailState => ({ status: 'loaded', staff })),
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
