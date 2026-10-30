import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { logInForTest } from '../../../core/auth/auth-testing';
import { fakePatient } from '../../admin/patients/patient-testing';
import { ClinicalRecordForm } from './clinical-record-form';
import { Icd10Diagnosis } from './clinical-record-api';
import { fakeClinicalRecord } from './clinical-record-testing';

const LUMBAGO: Icd10Diagnosis = { code: 'M54.5', description: 'Lumbago no especificado' };

describe('ClinicalRecordForm', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let component: ClinicalRecordForm;
  let element: HTMLElement;

  const form = () => component['form'];
  const errors = () =>
    Array.from(element.querySelectorAll('mat-error')).map((error) => error.textContent?.trim());

  async function open(url: string): Promise<void> {
    harness = await RouterTestingHarness.create();
    component = await harness.navigateByUrl(url, ClinicalRecordForm);
    element = harness.routeNativeElement as HTMLElement;
    backend.expectOne('/api/patients/15').flush(fakePatient(15, { occupation: 'Ingeniero civil' }));
    await settle();
  }

  /** The edit mode loads the patient and the history from the clinical record view. */
  async function openEdit(): Promise<void> {
    harness = await RouterTestingHarness.create();
    component = await harness.navigateByUrl(
      '/physio/patients/15/clinical-record/edit',
      ClinicalRecordForm,
    );
    element = harness.routeNativeElement as HTMLElement;
    const load = backend.expectOne('/api/patients/15/clinical-record');
    expect(load.request.method).toBe('GET');
    load.flush(fakeClinicalRecord(15));
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  async function settle(): Promise<void> {
    // The ICD-10 picker searches the catalog after its debounce
    await new Promise((resolve) => setTimeout(resolve, 350));
    harness.detectChanges();
    await harness.fixture.whenStable();
    backend.match((r) => r.url === '/api/icd10-diagnoses').forEach((r) => r.flush([LUMBAGO]));
  }

  async function submit(): Promise<void> {
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  function fillTreatment(): void {
    form().controls.treatment.setValue({
      chiefComplaint: ' Dolor lumbar al levantar peso ',
      currentIllness: 'Tres semanas de evolución',
      diagnosis: LUMBAGO,
      referringPhysician: 'Dr. Carlos Andrade',
      complementaryTests: 'Radiografía lumbar',
      severity: 'MODERATE',
      painType: 'Punzante',
      painLocation: 'Región lumbar',
      initialPainScore: 7,
      painFrequency: 'Intermitente',
      sessionFrequency: '3 veces por semana',
      goal: 'Reducir el dolor',
      plannedSessions: 12,
    });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'physio/patients/:patientId/clinical-record/new', component: ClinicalRecordForm },
          {
            path: 'physio/patients/:patientId/clinical-record/edit',
            component: ClinicalRecordForm,
            data: { mode: 'edit' },
          },
          {
            path: 'physio/patients/:patientId/clinical-record/:recordId/treatments/new',
            component: ClinicalRecordForm,
          },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    logInForTest('PHYSIOTHERAPIST', 'Lcda. Valeria Andrade', 7);
  });

  afterEach(() => backend.verify());

  it('shows the patient and the four sections of the initial assessment', async () => {
    await open('/physio/patients/15/clinical-record/new');

    expect(element.querySelector('h1')?.textContent).toContain('Nuevo Historial Clínico');
    const card = element.querySelector('.patient-card')!.textContent!;
    expect(card).toContain('Juan Carlos Pérez Gómez');
    expect(card).toContain('HC: 1710034065');
    expect(card).toContain('Ingeniero civil');
    const sections = Array.from(element.querySelectorAll('app-form-section .title')).map((t) =>
      t.textContent?.trim(),
    );
    expect(sections).toEqual([
      '1. Hábitos y antecedentes',
      '2. Información diagnóstica',
      '3. Evaluación del dolor',
      '4. Plan terapéutico',
    ]);
    expect(element.querySelector<HTMLInputElement>('input[readonly]')?.value).toBe(
      'Lcda. Valeria Andrade',
    );
  });

  it('validates in Spanish, including the EVA range and the planned sessions', async () => {
    await open('/physio/patients/15/clinical-record/new');
    fillTreatment();
    form().controls.treatment.patchValue({
      initialPainScore: 11,
      plannedSessions: 0,
      diagnosis: null,
    });
    await submit();

    expect(errors()).toEqual(
      expect.arrayContaining([
        'Complete este campo.',
        'La EVA debe ser un número entero de 0 a 10.',
        'Las sesiones planificadas deben ser un número entero mayor a 0.',
        'Seleccione un diagnóstico del catálogo CIE-10.',
      ]),
    );
    expect(element.querySelector('.error')?.textContent).toContain(
      'Revise los campos marcados antes de guardar.',
    );
    backend.expectNone('/api/patients/15/clinical-record');
  });

  it('creates the record with its first treatment and opens the record view', async () => {
    await open('/physio/patients/15/clinical-record/new');
    form().controls.history.setValue({
      habits: ' No fuma ',
      physicalActivity: 'Camina',
      allergies: 'Ninguna',
      currentMedication: 'Ninguna',
      personalHistory: 'Ninguno',
      surgicalHistory: 'Ninguno',
      familyHistory: 'Ninguno',
    });
    fillTreatment();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    await submit();

    const request = backend.expectOne('/api/patients/15/clinical-record');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(
      expect.objectContaining({
        habits: 'No fuma',
        treatment: expect.objectContaining({
          diagnosisCode: 'M54.5',
          chiefComplaint: 'Dolor lumbar al levantar peso',
          severity: 'MODERATE',
          initialPainScore: 7,
          plannedSessions: 12,
        }),
      }),
    );
    request.flush({ id: 40 });
    expect(snackBar).toHaveBeenCalledWith('Historial clínico creado correctamente.', 'Cerrar', {
      duration: 5000,
    });
    expect(navigate).toHaveBeenCalledWith('/physio/patients/15/clinical-record');
  });

  it('links to a new treatment when the patient already has a record', async () => {
    await open('/physio/patients/15/clinical-record/new');
    form().controls.history.setValue({
      habits: 'a',
      physicalActivity: 'b',
      allergies: 'c',
      currentMedication: 'd',
      personalHistory: 'e',
      surgicalHistory: 'f',
      familyHistory: 'g',
    });
    fillTreatment();
    await submit();
    backend
      .expectOne('/api/patients/15/clinical-record')
      .flush(
        { detail: 'El paciente ya tiene un historial clínico.', existingClinicalRecordId: 40 },
        { status: 409, statusText: 'Conflict' },
      );
    harness.detectChanges();
    await harness.fixture.whenStable();

    const notice = element.querySelector('.notice')!;
    expect(notice.textContent).toContain('El paciente ya tiene un historial clínico.');
    expect(notice.querySelector('a')?.getAttribute('href')).toBe(
      '/physio/patients/15/clinical-record/40/treatments/new',
    );
  });

  it('opens a new treatment without the habits and history section', async () => {
    await open('/physio/patients/15/clinical-record/40/treatments/new');

    expect(element.querySelector('h1')?.textContent).toContain('Nuevo Tratamiento');
    expect(element.textContent).not.toContain('Hábitos y antecedentes');
    fillTreatment();
    await submit();

    const request = backend.expectOne('/api/clinical-records/40/treatments');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(expect.objectContaining({ diagnosisCode: 'M54.5' }));
    expect(request.request.body).not.toHaveProperty('habits');
    request.flush(
      { detail: 'El paciente ya tiene un tratamiento activo.' },
      { status: 422, statusText: 'Unprocessable Content' },
    );
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(element.querySelector('.error')?.textContent).toContain(
      'El paciente ya tiene un tratamiento activo.',
    );
  });

  it('edits only the habits and history, preloaded from the record', async () => {
    await openEdit();

    expect(element.querySelector('h1')?.textContent).toContain('Editar Historial Clínico');
    const sections = Array.from(element.querySelectorAll('app-form-section .title')).map((t) =>
      t.textContent?.trim(),
    );
    expect(sections).toEqual(['1. Hábitos y antecedentes']);
    expect(element.querySelector('.patient-card')?.textContent).toContain(
      'Juan Carlos Pérez Gómez',
    );
    expect(form().controls.history.getRawValue()).toEqual({
      habits: 'No fuma; café diario',
      physicalActivity: 'Camina 30 minutos',
      allergies: 'Ninguna conocida',
      currentMedication: 'Ibuprofeno 400 mg',
      personalHistory: 'Hipertensión controlada',
      surgicalHistory: 'Apendicectomía (2010)',
      familyHistory: 'Madre con artrosis',
    });
    const cancel = Array.from(element.querySelectorAll('a')).find(
      (a) => a.textContent?.trim() === 'Cancelar',
    );
    expect(cancel?.getAttribute('href')).toBe('/physio/patients/15/clinical-record');
    expect(element.querySelector('button[type="submit"]')?.textContent).toContain(
      'Guardar cambios',
    );
  });

  it('saves the edited history with PUT and goes back to the record view', async () => {
    await openEdit();
    form().controls.history.patchValue({
      habits: ' Fuma 2 cigarrillos al día ',
      allergies: 'Penicilina',
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    await submit();

    const request = backend.expectOne('/api/patients/15/clinical-record');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      habits: 'Fuma 2 cigarrillos al día',
      physicalActivity: 'Camina 30 minutos',
      allergies: 'Penicilina',
      currentMedication: 'Ibuprofeno 400 mg',
      personalHistory: 'Hipertensión controlada',
      surgicalHistory: 'Apendicectomía (2010)',
      familyHistory: 'Madre con artrosis',
    });
    request.flush({ id: 40 });
    expect(snackBar).toHaveBeenCalledWith(
      'Historial clínico actualizado correctamente.',
      'Cerrar',
      {
        duration: 5000,
      },
    );
    expect(navigate).toHaveBeenCalledWith('/physio/patients/15/clinical-record');
  });

  it('does not save an emptied field and shows the server errors', async () => {
    await openEdit();
    form().controls.history.patchValue({ familyHistory: '' });
    await submit();
    expect(errors()).toContain('Complete este campo.');
    backend.expectNone((r) => r.method === 'PUT');

    form().controls.history.patchValue({ familyHistory: 'Padre hipertenso' });
    await submit();
    backend
      .expectOne('/api/patients/15/clinical-record')
      .flush(
        { detail: 'El paciente no tiene historial clínico.' },
        { status: 404, statusText: 'Not Found' },
      );
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(element.querySelector('.error')?.textContent).toContain(
      'El paciente no tiene historial clínico.',
    );
  });

  it('tells when the record to edit cannot be loaded', async () => {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/physio/patients/15/clinical-record/edit', ClinicalRecordForm);
    element = harness.routeNativeElement as HTMLElement;
    backend
      .expectOne('/api/patients/15/clinical-record')
      .flush(
        { detail: 'El paciente no tiene historial clínico.' },
        { status: 404, statusText: 'Not Found' },
      );
    harness.detectChanges();
    await harness.fixture.whenStable();

    expect(element.textContent).toContain('No se pudo cargar el historial');
    expect(element.textContent).toContain('El paciente no tiene historial clínico.');
    expect(element.querySelector('button[type="submit"]')?.hasAttribute('disabled')).toBe(true);
  });
});
