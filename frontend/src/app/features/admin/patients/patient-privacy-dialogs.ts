import { Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Observable } from 'rxjs';
import { DateField } from '../../../shared/forms/date-field';
import {
  DATA_REQUEST_TYPE_LABELS,
  DATA_REQUEST_TYPES,
  DataRequestStatus,
  DataRequestType,
} from '../../../shared/legal/privacy-labels';
import { todayIsoDate } from '../../../shared/people/patient-age';

const DIALOG_WIDTH = { width: '640px', maxWidth: 'calc(100vw - 32px)' };

/** A new request of the data subject: the right and the day it was received (today by default). */
export interface NewDataRequest {
  type: DataRequestType;
  receivedOn: string;
}

@Component({
  selector: 'app-register-request-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    ReactiveFormsModule,
    DateField,
  ],
  template: `
    <h2 mat-dialog-title>Registrar solicitud del titular</h2>
    <mat-dialog-content>
      <form class="form" [formGroup]="form" (ngSubmit)="save()" novalidate>
        <mat-form-field appearance="fill" subscriptSizing="dynamic">
          <mat-label>Derecho que ejerce</mat-label>
          <mat-select formControlName="type">
            @for (type of types; track type) {
              <mat-option [value]="type">{{ typeLabels[type] }}</mat-option>
            }
          </mat-select>
          @if (form.controls.type.hasError('required')) {
            <mat-error>Elija el tipo de solicitud.</mat-error>
          }
        </mat-form-field>
        <app-date-field
          label="Fecha de recepción"
          inputId="request-received-on"
          [control]="form.controls.receivedOn"
          [max]="today"
          [messages]="{ required: 'Ingrese la fecha de recepción.' }"
        />
        @if (form.controls.type.value === 'ERASURE') {
          <p class="note">
            La eliminación la acepta la dirección operativa: bloquea al paciente y borra sus datos
            de contacto, pero la historia clínica se conserva.
          </p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="button" (click)="save()">Registrar</button>
    </mat-dialog-actions>
  `,
  styles: `
    .form {
      display: grid;
      gap: var(--re-space-4);
      padding-top: var(--re-space-2);
    }
    .note {
      margin: 0;
      color: var(--re-text-muted);
      font-size: 0.9rem;
    }
  `,
})
export class RegisterRequestDialog {
  private readonly ref = inject<MatDialogRef<RegisterRequestDialog, NewDataRequest>>(MatDialogRef);

  protected readonly types = DATA_REQUEST_TYPES;
  protected readonly typeLabels = DATA_REQUEST_TYPE_LABELS;
  protected readonly today = todayIsoDate();
  protected readonly form = inject(NonNullableFormBuilder).group({
    type: ['' as DataRequestType | '', Validators.required],
    receivedOn: [this.today, Validators.required],
  });

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { type, receivedOn } = this.form.getRawValue();
    this.ref.close({ type: type as DataRequestType, receivedOn });
  }
}

export function openRegisterRequestDialog(
  dialog: MatDialog,
): Observable<NewDataRequest | undefined> {
  return dialog
    .open<RegisterRequestDialog, void, NewDataRequest>(RegisterRequestDialog, DIALOG_WIDTH)
    .afterClosed();
}

/** Closing of a request: resolved or rejected, with the response to the data subject. */
export interface RequestResponseData {
  status: Extract<DataRequestStatus, 'RESOLVED' | 'REJECTED'>;
  typeLabel: string;
}

@Component({
  selector: 'app-request-response-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  template: `
    <h2 mat-dialog-title>
      {{ data.status === 'RESOLVED' ? 'Resolver' : 'Rechazar' }} solicitud de
      {{ data.typeLabel.toLowerCase() }}
    </h2>
    <mat-dialog-content>
      <mat-form-field appearance="fill" class="response" subscriptSizing="dynamic">
        <mat-label>Respuesta al titular</mat-label>
        <textarea
          matInput
          id="request-response"
          rows="5"
          maxlength="2000"
          [formControl]="response"
        ></textarea>
        <mat-hint align="end">{{ response.value.length }}/2000</mat-hint>
        @if (response.invalid) {
          <mat-error>Escriba la respuesta al titular.</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="button" (click)="save()">
        {{ data.status === 'RESOLVED' ? 'Resolver' : 'Rechazar' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .response {
      width: 100%;
      padding-top: var(--re-space-2);
    }
  `,
})
export class RequestResponseDialog {
  private readonly ref = inject<MatDialogRef<RequestResponseDialog, string>>(MatDialogRef);
  protected readonly data = inject<RequestResponseData>(MAT_DIALOG_DATA);
  protected readonly response = inject(NonNullableFormBuilder).control('', [
    Validators.required,
    Validators.pattern(/\S/),
    Validators.maxLength(2000),
  ]);

  protected save(): void {
    if (this.response.invalid) {
      this.response.markAsTouched();
      return;
    }
    this.ref.close(this.response.value.trim());
  }
}

export function openRequestResponseDialog(
  dialog: MatDialog,
  data: RequestResponseData,
): Observable<string | undefined> {
  return dialog
    .open<RequestResponseDialog, RequestResponseData, string>(RequestResponseDialog, {
      ...DIALOG_WIDTH,
      data,
    })
    .afterClosed();
}
