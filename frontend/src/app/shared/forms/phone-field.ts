import { Component, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { PHONE_MESSAGE } from './phone';

/**
 * Phone field of the forms (MEJ-03): a mobile (09XXXXXXXX) or a landline with its province code, digits only. The
 * control must use `phoneValidator`; errors show after the field is touched.
 */
@Component({
  selector: 'app-phone-field',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatIconModule],
  template: `
    <mat-form-field appearance="fill" subscriptSizing="dynamic">
      <mat-label>{{ label() }}</mat-label>
      <mat-icon matPrefix aria-hidden="true">call</mat-icon>
      <input
        matInput
        [id]="inputId()"
        [formControl]="control()"
        type="tel"
        inputmode="numeric"
        maxlength="10"
        autocomplete="off"
        placeholder="09XXXXXXXX"
      />
      @if (control().hasError('required')) {
        <mat-error>Ingrese el teléfono.</mat-error>
      } @else if (control().hasError('phone')) {
        <mat-error>{{ phoneMessage }}</mat-error>
      }
    </mat-form-field>
  `,
  styles: `
    :host {
      display: block;
    }
    mat-form-field {
      width: 100%;
    }
    mat-icon[matPrefix] {
      margin: 0 4px 0 12px;
      color: var(--re-gray-400);
    }
  `,
})
export class PhoneField {
  readonly control = input.required<FormControl<string>>();
  readonly label = input<string>('Teléfono');
  readonly inputId = input<string>('phone-field');
  protected readonly phoneMessage = PHONE_MESSAGE;
}
