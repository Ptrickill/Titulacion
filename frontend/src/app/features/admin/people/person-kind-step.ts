import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { map, startWith, switchMap } from 'rxjs';
import { PersonType, StaffRole } from './people-api';
import { PERSON_ROLE_LABELS, STAFF_ROLES } from './people-labels';

/** The group of step 0: the type of person and, for the staff, the role of its account. */
export type PersonKindForm = FormGroup<{
  type: FormControl<PersonType | null>;
  role: FormControl<StaffRole | null>;
}>;

/**
 * Step 0 of "Registrar usuario" (MEJ-02): Paciente or Personal as two large choices and, for the staff, the role.
 * The errors show only after "Siguiente" marks the group as touched.
 */
@Component({
  selector: 'app-person-kind-step',
  imports: [ReactiveFormsModule, MatButtonToggleModule, MatIconModule],
  template: `
    <div class="kind" [formGroup]="group()">
      <p class="question" id="kind-type-label">¿A quién va a registrar?</p>
      <mat-button-toggle-group
        class="type-choice"
        formControlName="type"
        aria-labelledby="kind-type-label"
      >
        <mat-button-toggle value="PATIENT" id="person-type-patient">
          <mat-icon aria-hidden="true">personal_injury</mat-icon>
          <span class="choice-title">Paciente</span>
          <span class="choice-text"
            >Admisión y datos personales; el acceso al portal se crea desde su ficha.</span
          >
        </mat-button-toggle>
        <mat-button-toggle value="STAFF" id="person-type-staff">
          <mat-icon aria-hidden="true">badge</mat-icon>
          <span class="choice-title">Personal</span>
          <span class="choice-text"
            >Fisioterapia, administración o dirección; recibe su cuenta al registrarse.</span
          >
        </mat-button-toggle>
      </mat-button-toggle-group>
      @if (group().controls.type.touched && group().controls.type.invalid) {
        <p class="choice-error" role="alert">Seleccione el tipo de usuario.</p>
      }

      @if (isStaff()) {
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
      }
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

    .type-choice {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
      // The group clips its content by default, which hides the outer border of each card
      overflow: visible;
      border: none !important;

      mat-button-toggle {
        border: 1px solid var(--re-gray-200) !important;
        border-radius: var(--re-radius-md);
        background: var(--re-card);

        ::ng-deep .mat-button-toggle-label-content {
          display: grid;
          justify-items: start;
          gap: 4px;
          padding: 14px 16px;
          line-height: 1.4;
          text-align: left;
          white-space: normal;
        }

        &.mat-button-toggle-checked {
          border-color: var(--re-teal) !important;
          background: var(--re-teal-50);
          box-shadow: inset 0 0 0 1px var(--re-teal);
        }

        mat-icon {
          color: var(--re-text-teal);
        }
      }
    }

    .choice-title {
      color: var(--re-navy);
      font-size: 15px;
      font-weight: 700;
    }

    .choice-text,
    .note {
      color: var(--re-text-muted);
      font-size: 12.5px;
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
      font-size: 13px;
    }

    @media (max-width: 719.98px) {
      .type-choice {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class PersonKindStep {
  readonly group = input.required<PersonKindForm>();

  protected readonly roles = STAFF_ROLES;
  protected readonly roleLabels = PERSON_ROLE_LABELS;

  private readonly type = toSignal(
    toObservable(this.group).pipe(
      switchMap((group) =>
        group.controls.type.valueChanges.pipe(startWith(group.controls.type.value)),
      ),
    ),
    { initialValue: null },
  );
  protected readonly isStaff = computed(() => this.type() === 'STAFF');

  /** On a phone the three roles do not fit in one row: they go one under the other. */
  protected readonly narrow = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 719.98px)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
}
