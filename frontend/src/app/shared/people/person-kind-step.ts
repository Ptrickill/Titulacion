import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { map } from 'rxjs';
import { PersonType, StaffRole } from './people-api';
import { PERSON_ROLE_LABELS, STAFF_ROLES } from './people-labels';

/** The group of step 0: the type of person (STAFF, fixed by the operations director's page) and the role. */
export type PersonKindForm = FormGroup<{
  type: FormControl<PersonType | null>;
  role: FormControl<StaffRole | null>;
}>;

/**
 * Step 0 of "Registrar personal" (MEJ-02, MEJ-05): the role of the new account (fisioterapeuta, recepción or dirección
 * operativa). The reception registers only patients, so it never sees this step. The error shows only after
 * "Siguiente" marks the group as touched.
 */
@Component({
  selector: 'app-person-kind-step',
  imports: [ReactiveFormsModule, MatButtonToggleModule],
  template: `
    <div class="kind" [formGroup]="group()">
      <p class="question" id="kind-role-label">Rol de la cuenta</p>
      <mat-button-toggle-group
        class="role-choice"
        formControlName="role"
        aria-labelledby="kind-role-label"
        [vertical]="narrow()"
      >
        @for (role of roles; track role) {
          <mat-button-toggle [value]="role">{{ roleLabels[role] }}</mat-button-toggle>
        }
      </mat-button-toggle-group>
      @if (group().touched && group().hasError('roleRequired')) {
        <p class="choice-error" role="alert">Seleccione el rol.</p>
      }
      <p class="note">
        Al registrar, se crea la cuenta con el correo y los nombres del registro, y se envía el
        enlace de activación (válido por 48 horas).
      </p>
    </div>
  `,
  styles: `
    .kind {
      display: flex;
      flex-direction: column;
      gap: 10px;
      padding-top: 8px;
    }

    .question {
      margin: 4px 0 0;
      color: var(--re-navy);
      font-size: 14px;
      font-weight: 700;
    }

    .role-choice {
      align-self: flex-start;
      max-width: 100%;

      &.mat-button-toggle-vertical {
        align-self: stretch;
      }
    }

    .choice-error {
      margin: 0;
      color: var(--re-danger);
      font-size: 12px;
    }

    .note {
      margin: 4px 0 0;
      color: var(--re-text-muted);
      font-size: 13px;
    }
  `,
})
export class PersonKindStep {
  readonly group = input.required<PersonKindForm>();

  protected readonly roles = STAFF_ROLES;
  protected readonly roleLabels = PERSON_ROLE_LABELS;

  /** On a phone the three roles do not fit in one row: they go one under the other. */
  protected readonly narrow = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 719.98px)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
}
