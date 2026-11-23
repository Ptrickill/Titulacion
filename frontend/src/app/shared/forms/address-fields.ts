import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { startWith } from 'rxjs';
import { AddressValue, LocationCatalog, LocationCatalogApi } from './location-catalog';

/** Controls of a structured address (MEJ-03). The number is the only optional part. */
export type AddressForm = FormGroup<{
  provinceCode: FormControl<string>;
  cantonCode: FormControl<string>;
  parishCode: FormControl<string>;
  mainStreet: FormControl<string>;
  streetNumber: FormControl<string>;
  crossStreet: FormControl<string>;
  reference: FormControl<string>;
}>;

export function createAddressForm(value?: Partial<AddressValue> | null): AddressForm {
  const text = (initial: string | null | undefined, validators: ValidatorFn[]) =>
    new FormControl(initial ?? '', { nonNullable: true, validators });
  return new FormGroup({
    provinceCode: text(value?.provinceCode, [Validators.required]),
    cantonCode: text(value?.cantonCode, [Validators.required]),
    parishCode: text(value?.parishCode, [Validators.required]),
    mainStreet: text(value?.mainStreet, [Validators.required, Validators.maxLength(150)]),
    streetNumber: text(value?.streetNumber, [Validators.maxLength(20)]),
    crossStreet: text(value?.crossStreet, [Validators.required, Validators.maxLength(150)]),
    reference: text(value?.reference, [Validators.required, Validators.maxLength(255)]),
  });
}

/** The address as the API takes it: trimmed text and a blank number as null. */
export function addressValueOf(form: AddressForm): AddressValue {
  const value = form.getRawValue();
  return {
    provinceCode: value.provinceCode,
    cantonCode: value.cantonCode,
    parishCode: value.parishCode,
    mainStreet: value.mainStreet.trim(),
    streetNumber: value.streetNumber.trim() || null,
    crossStreet: value.crossStreet.trim(),
    reference: value.reference.trim(),
  };
}

/**
 * Structured address (MEJ-03): province, canton and parish in dependent lists from INEC's catalog
 * (GET /api/catalogs/locations, cached), then the main street, the number (optional), the cross street and a
 * reference. Choosing another province clears the canton and the parish; another canton clears the parish.
 */
@Component({
  selector: 'app-address-fields',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './address-fields.html',
  styleUrl: './address-fields.scss',
})
export class AddressFields implements OnInit {
  private readonly api = inject(LocationCatalogApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly group = input.required<AddressForm>();

  protected readonly catalog = signal<LocationCatalog | null>(null);
  protected readonly loadError = signal(false);
  private readonly provinceCode = signal('');
  private readonly cantonCode = signal('');

  protected readonly provinces = computed(() => this.catalog()?.provinces ?? []);
  protected readonly cantons = computed(
    () => this.provinces().find((p) => p.code === this.provinceCode())?.cantons ?? [],
  );
  protected readonly parishes = computed(
    () => this.cantons().find((c) => c.code === this.cantonCode())?.parishes ?? [],
  );

  ngOnInit(): void {
    const { provinceCode, cantonCode, parishCode } = this.group().controls;
    provinceCode.valueChanges
      .pipe(startWith(provinceCode.value), takeUntilDestroyed(this.destroyRef))
      .subscribe((code) => {
        if (code !== this.provinceCode() && this.provinceCode() !== '') {
          cantonCode.setValue('');
          parishCode.setValue('');
        }
        this.provinceCode.set(code);
      });
    cantonCode.valueChanges
      .pipe(startWith(cantonCode.value), takeUntilDestroyed(this.destroyRef))
      .subscribe((code) => {
        if (code !== this.cantonCode() && this.cantonCode() !== '' && code !== '') {
          parishCode.setValue('');
        }
        this.cantonCode.set(code);
      });
    this.load();
  }

  protected load(): void {
    this.loadError.set(false);
    this.api
      .catalog()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (catalog) => this.catalog.set(catalog),
        error: () => this.loadError.set(true),
      });
  }
}
