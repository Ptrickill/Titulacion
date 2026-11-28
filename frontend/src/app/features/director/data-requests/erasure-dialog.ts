import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Observable, startWith } from 'rxjs';
import { ErasureRequest } from './erasure-api';

/** What the director decided: accept (with the typed national ID) or reject, with the response. */
export type ErasureDecision =
  | { decision: 'accept'; nationalId: string; response: string }
  | { decision: 'reject'; response: string };

/** The decision the dialog opens for. */
export interface ErasureDialogData {
  erasure: ErasureRequest;
  decision: 'accept' | 'reject';
}

/**
 * Acceptance or rejection of an erasure request (MEJ-06). Accepting is irreversible, so the director types the
 * patient's national ID to confirm it, as in other destructive actions; both need the response to the data subject.
 */
@Component({
  selector: 'app-erasure-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  template: `
    <h2 mat-dialog-title>
      {{ accepting ? 'Aceptar' : 'Rechazar' }} la eliminación de {{ data.erasure.patientName }}
    </h2>
    <mat-dialog-content>
      @if (accepting) {
        <div class="warning" role="note">
          <mat-icon aria-hidden="true">warning</mat-icon>
          <div>
            <p>
              Es irreversible: el paciente queda bloqueado, se borran su correo, teléfono y
              dirección, se revocan sus consentimientos y se desactiva su acceso al portal.
            </p>
            <p>La historia clínica se conserva, como exige la normativa sanitaria.</p>
          </div>
        </div>
      }
      <form class="form" [formGroup]="form" novalidate>
        @if (accepting) {
          <mat-form-field appearance="fill" subscriptSizing="dynamic">
            <mat-label>Cédula del paciente</mat-label>
            <input
              matInput
              id="erasure-national-id"
              inputmode="numeric"
              maxlength="10"
              autocomplete="off"
              formControlName="nationalId"
            />
            <mat-hint>Escriba {{ data.erasure.nationalId }} para confirmar.</mat-hint>
            @if (form.controls.nationalId.touched && !matches()) {
              <mat-error>La cédula no coincide con la del paciente.</mat-error>
            }
          </mat-form-field>
        }
        <mat-form-field appearance="fill" subscriptSizing="dynamic">
          <mat-label>Respuesta al titular</mat-label>
          <textarea
            matInput
            id="erasure-response"
            rows="4"
            maxlength="2000"
            formControlName="response"
          ></textarea>
          @if (form.controls.response.invalid) {
            <mat-error>Escriba la respuesta al titular.</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button
        mat-flat-button
        type="button"
        [class.danger]="accepting"
        [disabled]="accepting && !matches()"
        (click)="save()"
      >
        {{ accepting ? 'Aceptar y eliminar datos' : 'Rechazar solicitud' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .warning {
      display: flex;
      gap: var(--re-space-3);
      margin-bottom: var(--re-space-4);
      padding: var(--re-space-3) var(--re-space-4);
      border: 1px solid var(--re-danger-border);
      border-radius: var(--re-radius-sm);
      background: var(--re-danger-bg);
      color: var(--re-danger);
    }
    .warning mat-icon {
      flex-shrink: 0;
    }
    .warning p {
      margin: 0 0 var(--re-space-2);
    }
    .warning p:last-child {
      margin-bottom: 0;
    }
    .form {
      display: grid;
      gap: var(--re-space-4);
    }
    .danger {
      --mat-button-filled-container-color: var(--re-danger);
      --mat-button-filled-label-text-color: var(--re-card);
    }
  `,
})
export class ErasureDialog {
  private readonly ref = inject<MatDialogRef<ErasureDialog, ErasureDecision>>(MatDialogRef);
  protected readonly data = inject<ErasureDialogData>(MAT_DIALOG_DATA);
  protected readonly accepting = this.data.decision === 'accept';

  protected readonly form = inject(NonNullableFormBuilder).group({
    nationalId: [''],
    response: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(2000)]],
  });
  private readonly typed = toSignal(
    this.form.controls.nationalId.valueChanges.pipe(startWith('')),
    { initialValue: '' },
  );
  protected readonly matches = computed(() => this.typed().trim() === this.data.erasure.nationalId);

  protected save(): void {
    if (this.form.controls.response.invalid || (this.accepting && !this.matches())) {
      this.form.markAllAsTouched();
      return;
    }
    const response = this.form.controls.response.value.trim();
    this.ref.close(
      this.accepting
        ? { decision: 'accept', nationalId: this.typed().trim(), response }
        : { decision: 'reject', response },
    );
  }
}

export function openErasureDialog(
  dialog: MatDialog,
  data: ErasureDialogData,
): Observable<ErasureDecision | undefined> {
  return dialog
    .open<ErasureDialog, ErasureDialogData, ErasureDecision>(ErasureDialog, {
      width: '560px',
      maxWidth: 'calc(100vw - 32px)',
      data,
    })
    .afterClosed();
}
