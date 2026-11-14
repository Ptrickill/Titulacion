import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { logInForTest } from '../../core/auth/auth-testing';
import { PatientDashboard } from './patient-dashboard';
import { PortalTreatment, TreatmentProgress } from './portal-api';
import { fakeCurve, fakeProgress, fakeTreatment } from './portal-testing';

const ACTIVE = fakeTreatment();
const FINISHED = fakeTreatment({
  id: 20,
  diagnosis: { code: 'M75.1', description: 'Síndrome del manguito rotatorio' },
  status: 'COMPLETED',
  active: false,
  startDate: '2026-03-02',
  responsibleName: 'Lcdo. Mateo Ríos',
  plannedSessions: 10,
  completedSessions: 10,
});
const ABANDONED = fakeTreatment({
  id: 10,
  status: 'ABANDONED',
  active: false,
  startDate: '2025-08-11',
  responsibleName: null,
  completedSessions: 0,
});

describe('PatientDashboard', () => {
  let fixture: ComponentFixture<PatientDashboard>;
  let backend: HttpTestingController;
  let loader: HarnessLoader;
  let element: HTMLElement;

  const clean = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
  const text = (selector: string) => clean(element.querySelector(selector));
  const emptyTitle = () => text('app-empty-state .title');
  const progressCard = () => element.querySelector('app-treatment-progress-card');
  const painCard = () => element.querySelector('app-pain-evolution-card');
  const loadingBar = () => element.querySelector('mat-progress-bar');

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** Opens the portal; the list of treatments stays pending. */
  async function open(fullName = 'Rosa Elena Ortiz Vega'): Promise<void> {
    logInForTest('PATIENT', fullName);
    fixture = TestBed.createComponent(PatientDashboard);
    loader = TestbedHarnessEnvironment.loader(fixture);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  async function answerTreatments(treatments: PortalTreatment[]): Promise<void> {
    backend.expectOne('/api/portal/treatments').flush(treatments);
    await settle();
  }

  async function answerProgress(treatmentId: number, progress: TreatmentProgress): Promise<void> {
    backend.expectOne(`/api/portal/treatments/${treatmentId}/progress`).flush(progress);
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('greets the patient and shows the diagnosis and who is in charge of the treatment', async () => {
    await open();
    expect(text('app-hero-banner .title')).toBe('¡Hola, Rosa!');
    expect(loadingBar()).not.toBeNull();

    await answerTreatments([ACTIVE]);
    await answerProgress(30, fakeProgress());

    expect(text('app-hero-banner .message')).toBe(
      'Tu tratamiento para Lumbago no especificado está a cargo de Lcda. Valeria Andrade.',
    );
    expect(loadingBar()).toBeNull();
  });

  it('shows the progress of the plan and the evolution of the pain', async () => {
    await open();
    await answerTreatments([ACTIVE]);
    await answerProgress(30, fakeProgress());

    expect(clean(progressCard()?.querySelector('.completed'))).toBe('4');
    expect(clean(progressCard()?.querySelector('.planned'))).toBe('de 12 sesiones');
    expect(clean(progressCard()?.querySelector('.summary'))).toBe('¡Completado un 33% de tu plan!');
    expect(painCard()?.querySelectorAll('.bar')).toHaveLength(5);
    expect(clean(painCard()?.querySelector('.summary span'))).toBe(
      'Reducción de dolor lograda: 3 puntos en la escala visual analógica.',
    );
  });

  it('has no selector when the patient has a single treatment', async () => {
    await open();
    await answerTreatments([ACTIVE]);
    await answerProgress(30, fakeProgress());

    expect(element.querySelector('mat-select')).toBeNull();
  });

  it('offers a selector with every treatment and shows the active one by default', async () => {
    await open();
    await answerTreatments([ACTIVE, FINISHED, ABANDONED]);
    await answerProgress(30, fakeProgress());

    const select = await loader.getHarness(MatSelectHarness);
    expect(await select.getValueText()).toBe(
      'Lumbago no especificado · Activo · desde 01/10/2026',
    );
    await select.open();
    const options = await select.getOptions();
    expect(await Promise.all(options.map((option) => option.getText()))).toEqual([
      'Lumbago no especificado · Activo · desde 01/10/2026',
      'Síndrome del manguito rotatorio · Finalizado · desde 02/03/2026',
      'Lumbago no especificado · Abandonado · desde 11/08/2025',
    ]);
    await select.close();
  });

  it('loads the progress of the treatment chosen in the selector', async () => {
    await open();
    await answerTreatments([ACTIVE, FINISHED]);
    await answerProgress(30, fakeProgress());

    const select = await loader.getHarness(MatSelectHarness);
    await select.clickOptions({ text: /manguito/ });
    await answerProgress(
      20,
      fakeProgress({
        treatmentId: 20,
        completedSessions: 10,
        plannedSessions: 10,
        percentage: 100,
        lastPainScore: 1,
        painReduction: 6,
        painCurve: fakeCurve(1),
      }),
    );

    expect(text('app-hero-banner .message')).toBe(
      'Tu tratamiento para Síndrome del manguito rotatorio finalizó.',
    );
    expect(clean(progressCard()?.querySelector('.summary'))).toBe(
      '¡Completaste tu plan de sesiones!',
    );
    expect(painCard()?.querySelectorAll('.bar')).toHaveLength(2);
  });

  it('describes a treatment without responsible or abandoned by its status', async () => {
    await open();
    await answerTreatments([fakeTreatment({ responsibleName: null }), ABANDONED]);
    await answerProgress(30, fakeProgress());
    expect(text('app-hero-banner .message')).toBe(
      'Tu tratamiento para Lumbago no especificado está activo.',
    );

    const select = await loader.getHarness(MatSelectHarness);
    await select.clickOptions({ text: /Abandonado/ });
    await answerProgress(
      10,
      fakeProgress({
        treatmentId: 10,
        completedSessions: 0,
        percentage: 0,
        lastPainScore: null,
        painReduction: null,
        painCurve: fakeCurve(),
      }),
    );

    expect(text('app-hero-banner .message')).toBe(
      'Tu tratamiento para Lumbago no especificado figura como abandonado.',
    );
    expect(clean(painCard()?.querySelector('.summary span'))).toBe(
      'Aún no hay sesiones realizadas.',
    );
  });

  it('tells a patient without treatments that there is nothing yet, without asking for a progress', async () => {
    await open();
    await answerTreatments([]);

    expect(emptyTitle()).toBe('Aún no tienes un tratamiento registrado');
    expect(progressCard()).toBeNull();
    expect(painCard()).toBeNull();
    expect(loadingBar()).toBeNull();
    expect(text('app-hero-banner .message')).toBe(
      'Aquí puedes consultar el progreso de tu tratamiento y la evolución de tu dolor.',
    );
  });

  it('explains that the account has no linked patient (404), without a retry', async () => {
    await open();
    backend.expectOne('/api/portal/treatments').flush(
      { title: 'Recurso no encontrado', detail: 'La cuenta no tiene un paciente vinculado.' },
      { status: 404, statusText: 'Not Found' },
    );
    await settle();

    expect(emptyTitle()).toBe('Tu cuenta aún no está vinculada a un paciente');
    expect(element.querySelector('.load-error')).toBeNull();
    expect(element.querySelectorAll('button')).toHaveLength(0);
  });

  it('shows the error of the list and loads it again with "Reintentar"', async () => {
    await open();
    backend
      .expectOne('/api/portal/treatments')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await settle();

    const error = element.querySelector('.load-error')!;
    expect(error.getAttribute('role')).toBe('alert');
    expect(clean(error.querySelector('span'))).toBe('No se pudo cargar tu tratamiento.');

    error.querySelector('button')!.click();
    await answerTreatments([ACTIVE]);
    await answerProgress(30, fakeProgress());

    expect(element.querySelector('.load-error')).toBeNull();
    expect(progressCard()).not.toBeNull();
  });

  it('shows the error of the progress and asks for it again with "Reintentar"', async () => {
    await open();
    await answerTreatments([ACTIVE]);
    backend
      .expectOne('/api/portal/treatments/30/progress')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(clean(element.querySelector('.load-error span'))).toBe('No se pudo cargar el avance.');
    expect(progressCard()).toBeNull();

    element.querySelector<HTMLButtonElement>('.load-error button')!.click();
    await settle();
    await answerProgress(30, fakeProgress());

    expect(element.querySelector('.load-error')).toBeNull();
    expect(progressCard()).not.toBeNull();
  });

  it('offers no way to edit: no links, no form fields and no buttons besides the selector', async () => {
    await open();
    await answerTreatments([ACTIVE, FINISHED]);
    await answerProgress(30, fakeProgress());

    expect(element.querySelectorAll('a, input, textarea, button')).toHaveLength(0);
    expect(element.textContent).not.toMatch(/Editar|Corregir|Registrar|Eliminar|Guardar/);
  });

  it('greets without a name when the session has none', async () => {
    await open(' ');
    expect(text('app-hero-banner .title')).toBe('¡Hola!');
    await answerTreatments([]);
  });
});
