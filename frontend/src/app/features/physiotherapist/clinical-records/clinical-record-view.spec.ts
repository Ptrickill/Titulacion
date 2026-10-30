import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ClinicalRecordView } from './clinical-record-view';
import { fakeActiveTreatment, fakeClinicalRecord } from './clinical-record-testing';

describe('ClinicalRecordView', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let element: HTMLElement;

  const text = (selector: string) =>
    element.querySelector(selector)?.textContent?.replace(/\s+/g, ' ') ?? '';
  const section = (title: string) =>
    Array.from(element.querySelectorAll('section')).find((s) =>
      s.querySelector('h2')?.textContent?.includes(title),
    );
  const link = (label: string) =>
    Array.from(element.querySelectorAll('a')).find((a) => a.textContent?.trim().endsWith(label));

  async function open(): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/physio/patients/15/clinical-record', ClinicalRecordView);
    element = harness.routeNativeElement as HTMLElement;
  }

  async function respond(body: object, status = 200): Promise<void> {
    const request = backend.expectOne('/api/patients/15/clinical-record');
    expect(request.request.method).toBe('GET');
    if (status === 200) {
      request.flush(body);
    } else {
      request.flush(body, { status, statusText: 'Error' });
    }
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'physio/patients/:patientId/clinical-record', component: ClinicalRecordView },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('shows the patient, the diagnosis, the pain assessment and the plan with its responsible', async () => {
    await open();
    await respond(fakeClinicalRecord(15));

    expect(text('h1')).toContain('Expediente e Historial Clínico de Fisioterapia');
    const card = text('.patient-card');
    expect(card).toContain('Juan Carlos Pérez Gómez');
    expect(card).toContain('HC: 1710034065');
    expect(card).toContain('Fecha de ingreso: 08/07/2026');
    expect(card).toContain('juan.perez@correo.ec');
    expect(card).toContain('Av. Amazonas N32-12, Quito');
    expect(card).toContain('Seguro: IESS');

    const diagnosis = section('Información diagnóstica')!.textContent!.replace(/\s+/g, ' ');
    expect(diagnosis).toContain('Moderada');
    expect(diagnosis).toContain('Dolor muscular asociado al ejercicio');
    expect(diagnosis).toContain('M51.1 Trastorno de disco lumbar con radiculopatía');
    expect(diagnosis).toContain('Dr. Carlos Andrade');
    expect(diagnosis).toContain('Radiografía de columna dorsal');

    const pain = section('Evaluación del dolor')!.textContent!.replace(/\s+/g, ' ');
    expect(pain).toContain('EVA: 7/10');
    expect(section('Evaluación del dolor')!.querySelector('app-status-badge')!.classList).toContain(
      'badge-danger',
    );
    expect(pain).toContain('Presión');
    expect(pain).toContain('Pecho y espalda alta');

    const plan = section('Plan terapéutico y asignación clínica')!.textContent!.replace(
      /\s+/g,
      ' ',
    );
    expect(plan).toContain('12 Sesiones Planificadas');
    expect(plan).toContain('Reducción de dolor y fortalecimiento lumbar progresivo');
    expect(plan).toContain('3 veces por semana');
    expect(plan).toContain('01/10/2026');
    expect(plan).toContain('Activo');
    expect(plan).toContain('Lcda. Valeria Andrade');
    expect(link('Plan y asignación')?.getAttribute('href')).toBe(
      '/physio/patients/15/treatments/30',
    );
  });

  it('shows the habits and history and the previous treatments', async () => {
    await open();
    await respond(
      fakeClinicalRecord(15, {
        clinicalRecord: {
          ...fakeClinicalRecord(15).clinicalRecord,
          updatedAt: '2026-10-29T20:30:00Z',
        },
      }),
    );

    const history = section('Hábitos y antecedentes')!;
    const fields = Array.from(history.querySelectorAll('dl > div')).map((field) => [
      field.querySelector('dt')?.textContent?.trim(),
      field.querySelector('dd')?.textContent?.trim(),
    ]);
    expect(fields).toHaveLength(7);
    expect(fields[0]).toEqual(['Hábitos', 'No fuma; café diario']);
    expect(fields[6]).toEqual(['Antecedentes familiares', 'Madre con artrosis']);
    expect(history.textContent).toContain('Última edición: 29/10/2026');

    const previous = section('Tratamientos anteriores')!;
    const row = previous.querySelector('li')!.textContent!.replace(/\s+/g, ' ');
    expect(row).toContain('M54.5 Lumbago no especificado');
    expect(row).toContain('Inicio: 10/03/2025');
    expect(row).toContain('Cierre: 02/05/2025');
    expect(row).toContain('Responsable: Lcdo. Martín Cevallos');
    expect(row).toContain('Finalizado');
    expect(previous.querySelector('a')?.getAttribute('href')).toBe(
      '/physio/patients/15/treatments/20',
    );
  });

  it('opens the edit form and keeps "Ver sesiones" disabled', async () => {
    await open();
    await respond(fakeClinicalRecord(15));

    expect(link('Editar historial')?.getAttribute('href')).toBe(
      '/physio/patients/15/clinical-record/edit',
    );
    const sessions = Array.from(element.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Ver sesiones'),
    )!;
    expect(sessions.disabled).toBe(true);
    expect(sessions.textContent).toContain('(disponible próximamente)');
  });

  it('offers a new treatment when none is active', async () => {
    await open();
    await respond(fakeClinicalRecord(15, { activeTreatment: null }));

    expect(section('Plan terapéutico y asignación clínica')).toBeUndefined();
    expect(element.textContent).toContain('El paciente no tiene un tratamiento activo.');
    expect(link('Nuevo tratamiento')?.getAttribute('href')).toBe(
      '/physio/patients/15/clinical-record/40/treatments/new',
    );
  });

  it('marks a low EVA as mild pain and says when there is no previous treatment', async () => {
    await open();
    await respond(
      fakeClinicalRecord(15, {
        activeTreatment: fakeActiveTreatment(30, { initialPainScore: 2, currentResponsible: null }),
        previousTreatments: [],
      }),
    );

    const eva = section('Evaluación del dolor')!.querySelector('app-status-badge')!;
    expect(eva.textContent).toContain('EVA: 2/10');
    expect(eva.classList).toContain('badge-success');
    expect(element.textContent).toContain('Sin responsable vigente.');
    expect(element.textContent).toContain('Sin tratamientos anteriores.');
  });

  it('offers to create the record when the patient has none', async () => {
    await open();
    await respond({ status: 404, detail: 'El paciente no tiene historial clínico.' }, 404);

    expect(element.textContent).toContain('No se pudo abrir el expediente');
    expect(element.textContent).toContain('El paciente no tiene historial clínico.');
    expect(link('Crear historial')?.getAttribute('href')).toBe(
      '/physio/patients/15/clinical-record/new',
    );
    expect(link('Editar historial')?.getAttribute('aria-disabled')).toBe('true');
  });

  it('does not offer to create a record for an unknown patient', async () => {
    await open();
    await respond({ status: 404, detail: 'El paciente no existe.' }, 404);

    expect(element.textContent).toContain('El paciente no existe.');
    expect(link('Crear historial')).toBeUndefined();
  });
});
