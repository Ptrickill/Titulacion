import { Component, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';

let nextId = 0;

/**
 * A yes/no question answered with two toggles, "Sí" and "No" (MEJ-03: "¿Tiene seguro de salud?"). The control
 * holds true, false or null (not answered yet); the error shows after an answer was attempted or the step was
 * left without one.
 */
@Component({
  selector: 'app-yes-no-toggle',
  imports: [ReactiveFormsModule, MatButtonToggleModule],
  template: `
    <div class="question" [id]="labelId">{{ label() }}</div>
    <mat-button-toggle-group
      [formControl]="control()"
      [attr.aria-labelledby]="labelId"
      hideSingleSelectionIndicator
      (change)="control().markAsTouched()"
    >
      <mat-button-toggle [value]="true">Sí</mat-button-toggle>
      <mat-button-toggle [value]="false">No</mat-button-toggle>
    </mat-button-toggle-group>
    @if (control().invalid && control().touched) {
      <p class="error" role="alert">{{ requiredMessage() }}</p>
    }
  `,
  styles: `
    :host {
      display: block;
      margin-bottom: 12px;
    }
    .question {
      margin: 0 0 6px;
      color: var(--re-gray-700);
      font-size: 13px;
      font-weight: 600;
    }
    mat-button-toggle-group {
      --mat-standard-button-toggle-height: 40px;
    }
    .error {
      margin: 4px 0 0;
      color: var(--re-danger);
      font-size: 12px;
    }
  `,
})
export class YesNoToggle {
  readonly control = input.required<FormControl<boolean | null>>();
  readonly label = input.required<string>();
  readonly requiredMessage = input<string>('Elija Sí o No.');
  protected readonly labelId = `yes-no-${nextId++}`;
}
