import { Component, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ControlValueAccessor, FormControl, NgControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { ErrorStateMatcher } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  map,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { ClinicalRecordApi, Icd10Diagnosis } from './clinical-record-api';

/**
 * ICD-10 diagnosis selector (HU-07): searches the catalog by code or description (accents ignored) as the user
 * types and only accepts an entry chosen from the list. Use it as a form control (formControlName); its value is
 * the chosen {@link Icd10Diagnosis}, or null while none is chosen.
 */
@Component({
  selector: 'app-icd10-picker',
  imports: [
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <mat-form-field appearance="fill" subscriptSizing="dynamic">
      <mat-label>{{ label() }}</mat-label>
      <mat-icon matPrefix aria-hidden="true">search</mat-icon>
      <input
        matInput
        [id]="inputId()"
        [formControl]="search"
        [matAutocomplete]="catalog"
        [errorStateMatcher]="errorMatcher"
        (input)="onInput()"
        (blur)="onTouched()"
        placeholder="Buscar por código o descripción"
        autocomplete="off"
      />
      @if (loading()) {
        <mat-spinner matSuffix diameter="18" aria-label="Buscando diagnósticos" />
      }
      <mat-autocomplete
        #catalog="matAutocomplete"
        [displayWith]="display"
        (optionSelected)="choose($event.option.value)"
      >
        @for (diagnosis of options(); track diagnosis.code) {
          <mat-option [value]="diagnosis">
            <span class="code">{{ diagnosis.code }}</span> {{ diagnosis.description }}
          </mat-option>
        } @empty {
          @if (!loading()) {
            <mat-option disabled>Ningún diagnóstico del catálogo coincide.</mat-option>
          }
        }
      </mat-autocomplete>
      <mat-hint>Seleccione un diagnóstico del catálogo CIE-10.</mat-hint>
      <mat-error>Seleccione un diagnóstico del catálogo CIE-10.</mat-error>
    </mat-form-field>
  `,
  styles: `
    :host {
      display: block;
    }
    mat-form-field {
      width: 100%;
    }
    .code {
      margin-right: 6px;
      color: var(--re-text-teal);
      font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
      font-weight: 700;
    }
  `,
})
export class Icd10Picker implements ControlValueAccessor {
  private readonly api = inject(ClinicalRecordApi);
  private readonly ngControl = inject(NgControl, { self: true, optional: true });

  readonly label = input('Diagnóstico principal (CIE-10)');
  readonly inputId = input('icd10-picker');

  /** What the user types, or the chosen diagnosis (shown with displayWith). */
  protected readonly search = new FormControl<string | Icd10Diagnosis>('', { nonNullable: true });
  protected readonly options = signal<Icd10Diagnosis[]>([]);
  protected readonly loading = signal(false);

  /** Shows the error of the outer form control (e.g. required) on the search input. */
  protected readonly errorMatcher: ErrorStateMatcher = {
    isErrorState: () => !!this.ngControl?.invalid && !!this.ngControl?.touched,
  };

  private onChange: (value: Icd10Diagnosis | null) => void = () => undefined;
  protected onTouched: () => void = () => undefined;

  constructor() {
    if (this.ngControl) {
      this.ngControl.valueAccessor = this;
    }
    this.search.valueChanges
      .pipe(
        startWith(''),
        filter((value): value is string => typeof value === 'string'),
        map((value) => value.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        tap(() => this.loading.set(true)),
        switchMap((query) => this.api.searchDiagnoses(query).pipe(catchError(() => of([])))),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((diagnoses) => {
        this.loading.set(false);
        this.options.set(diagnoses);
      });
  }

  protected display(value: string | Icd10Diagnosis | null): string {
    return typeof value === 'string' ? value : value ? `${value.code} — ${value.description}` : '';
  }

  protected choose(diagnosis: Icd10Diagnosis): void {
    this.onChange(diagnosis);
  }

  /** Typing again discards the chosen diagnosis until a new one is picked from the list. */
  protected onInput(): void {
    this.onChange(null);
  }

  writeValue(value: Icd10Diagnosis | null): void {
    this.search.setValue(value ?? '', { emitEvent: false });
  }

  registerOnChange(fn: (value: Icd10Diagnosis | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    if (disabled) {
      this.search.disable({ emitEvent: false });
    } else {
      this.search.enable({ emitEvent: false });
    }
  }
}
