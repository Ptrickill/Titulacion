import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { Icd10Diagnosis } from './clinical-record-api';
import { Icd10Picker } from './icd10-picker';

@Component({
  imports: [ReactiveFormsModule, Icd10Picker],
  template: `<app-icd10-picker [formControl]="diagnosis" />`,
})
class Host {
  readonly diagnosis = new FormControl<Icd10Diagnosis | null>(null, Validators.required);
}

const LUMBAGO: Icd10Diagnosis = { code: 'M54.5', description: 'Lumbago no especificado' };

describe('Icd10Picker', () => {
  let fixture: ComponentFixture<Host>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const input = () => element.querySelector<HTMLInputElement>('input')!;
  const picker = () =>
    fixture.debugElement.query(By.directive(Icd10Picker)).componentInstance as Icd10Picker;

  async function waitForDebounce(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 350));
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Host);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('loads the start of the catalog and searches as the user types', async () => {
    await waitForDebounce();
    backend
      .expectOne((r) => r.url === '/api/icd10-diagnoses' && r.params.get('q') === '')
      .flush([LUMBAGO]);

    input().value = '  ciatica ';
    input().dispatchEvent(new Event('input'));
    await waitForDebounce();

    backend
      .expectOne((r) => r.url === '/api/icd10-diagnoses' && r.params.get('q') === 'ciatica')
      .flush([{ code: 'M54.4', description: 'Lumbago con ciática' }]);
    await fixture.whenStable();
    expect(picker()['options']()).toEqual([{ code: 'M54.4', description: 'Lumbago con ciática' }]);
  });

  it('only takes a diagnosis chosen from the list; typing again clears it', async () => {
    await waitForDebounce();
    backend.expectOne((r) => r.url === '/api/icd10-diagnoses').flush([LUMBAGO]);

    picker()['choose'](LUMBAGO);
    expect(fixture.componentInstance.diagnosis.value).toEqual(LUMBAGO);

    input().value = 'M54';
    input().dispatchEvent(new Event('input'));
    expect(fixture.componentInstance.diagnosis.value).toBeNull();
    await waitForDebounce();
    backend.expectOne((r) => r.url === '/api/icd10-diagnoses').flush([]);
  });

  it('shows the chosen value as code and description and the required error when touched', async () => {
    await waitForDebounce();
    backend.expectOne((r) => r.url === '/api/icd10-diagnoses').flush([]);

    fixture.componentInstance.diagnosis.setValue(LUMBAGO);
    await fixture.whenStable();
    expect(input().value).toBe('M54.5 — Lumbago no especificado');

    fixture.componentInstance.diagnosis.setValue(null);
    fixture.componentInstance.diagnosis.markAsTouched();
    await fixture.whenStable();
    expect(element.querySelector('mat-error')?.textContent).toContain(
      'Seleccione un diagnóstico del catálogo CIE-10.',
    );
  });
});
