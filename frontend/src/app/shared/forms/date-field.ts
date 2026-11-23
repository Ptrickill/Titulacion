import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { fromIsoDate, toIsoDate } from './ec-date-adapter';
import { ErrorMessages, errorStateOf, firstErrorMessage } from './field-errors';

let nextId = 0;

/**
 * Date field of the forms (MEJ-03): typed as dd/mm/aaaa or chosen in the calendar (mat-datepicker, es-EC). The
 * bound control keeps the API value, yyyy-MM-dd ('' when empty). A typed date that does not exist adds the
 * `invalidDate` error to the control, so the form never takes a corrected date. Errors show after the field is
 * touched.
 */
@Component({
  selector: 'app-date-field',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatDatepickerModule],
  template: `
    <mat-form-field appearance="fill" subscriptSizing="dynamic">
      <mat-label>{{ label() }}</mat-label>
      <input
        matInput
        [id]="inputId()"
        [formControl]="typed"
        [matDatepicker]="picker"
        [min]="minDate()"
        [max]="maxDate()"
        [errorStateMatcher]="errorState"
        [required]="required()"
        placeholder="dd/mm/aaaa"
        autocomplete="off"
        (blur)="control().markAsTouched()"
      />
      <mat-datepicker-toggle
        matIconSuffix
        [for]="picker"
        [attr.aria-label]="'Abrir calendario: ' + label()"
      />
      <mat-datepicker #picker (closed)="control().markAsTouched()" />
      @if (hint()) {
        <mat-hint>{{ hint() }}</mat-hint>
      }
      <mat-error>{{ message() }}</mat-error>
    </mat-form-field>
  `,
  styles: `
    :host {
      display: block;
    }
    mat-form-field {
      width: 100%;
    }
  `,
})
export class DateField implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  /** The form control, with the API value yyyy-MM-dd. */
  readonly control = input.required<FormControl<string>>();
  readonly label = input.required<string>();
  readonly messages = input<ErrorMessages>({});
  readonly hint = input<string>('');
  /** yyyy-MM-dd limits of the calendar. */
  readonly min = input<string>('');
  readonly max = input<string>('');
  readonly inputId = input<string>(`date-field-${nextId++}`);

  /** The typed text, as a date of the adapter (null when empty, an invalid date when it does not exist). */
  protected readonly typed = new FormControl<Date | null>(null);
  protected readonly minDate = computed(() => fromIsoDate(this.min()));
  protected readonly maxDate = computed(() => fromIsoDate(this.max()));
  protected readonly errorState = errorStateOf(() => this.control());
  /** Shows the required mark (*) like the other fields when the control requires a date. */
  protected readonly required = computed(() => this.control().hasValidator(Validators.required));
  private readonly unparsable = signal(false);

  protected message(): string {
    return (
      firstErrorMessage(this.control(), {
        invalidDate: 'Ingrese una fecha válida en formato dd/mm/aaaa.',
        ...this.messages(),
      }) ?? ''
    );
  }

  ngOnInit(): void {
    const control = this.control();
    control.addValidators(this.notUnparsable);
    this.typed.setValue(fromIsoDate(control.value), { emitEvent: false });

    // The control may change from outside (e.g. the form is filled to edit a patient)
    control.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((value) => {
      if (value !== toIsoDate(this.typed.value) && !this.unparsable()) {
        this.typed.setValue(fromIsoDate(value), { emitEvent: false });
      }
    });
    this.typed.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((date) => {
      // The datepicker input reports text it cannot read as an error of its own control
      const invalid =
        this.typed.hasError('matDatepickerParse') || (!!date && isNaN(date.getTime()));
      this.unparsable.set(invalid);
      control.setValue(invalid ? '' : toIsoDate(date));
      control.markAsDirty();
    });
  }

  private readonly notUnparsable = (_: AbstractControl): ValidationErrors | null =>
    this.unparsable() ? { invalidDate: true } : null;
}
