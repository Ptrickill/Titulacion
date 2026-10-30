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

  it('creates the record with its first treatment and goes back to the list', async () => {
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
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
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
    expect(navigate).toHaveBeenCalledWith(['/physio/patients']);
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
});
