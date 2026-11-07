import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { logInForTest } from '../../../core/auth/auth-testing';
import { fakePatient } from '../../admin/patients/patient-testing';
import { fakeTreatment } from '../clinical-records/treatment-testing';
import { fakeSession, pageOf } from './session-testing';
import { TherapySessionForm } from './therapy-session-form';

describe('TherapySessionForm', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let component: TherapySessionForm;
  let element: HTMLElement;

  const form = () => component['form'];
  const readonlyValues = () =>
    Array.from(element.querySelectorAll<HTMLInputElement>('input[readonly]')).map((i) => i.value);
  const errors = () =>
    Array.from(element.querySelectorAll('mat-error')).map((error) => error.textContent?.trim());
  const submitButton = () =>
    Array.from(element.querySelectorAll('button')).find((b) => b.type === 'submit');

  async function settle(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  async function openNew(treatmentStatus: 'ACTIVE' | 'COMPLETED' = 'ACTIVE'): Promise<void> {
    harness = await RouterTestingHarness.create();
    component = await harness.navigateByUrl(
      '/physio/patients/15/treatments/30/sessions/new',
      TherapySessionForm,
    );
    element = harness.routeNativeElement as HTMLElement;
    backend.expectOne('/api/patients/15').flush(fakePatient(15));
    backend.expectOne('/api/treatments/30').flush(fakeTreatment(30, { status: treatmentStatus }));
    const count = backend.expectOne((r) => r.url === '/api/treatments/30/therapy-sessions');
    expect(count.request.params.get('size')).toBe('1');
    count.flush(pageOf([fakeSession(90)], 3));
    await settle();
  }

  async function openEdit(responsibleId = 7): Promise<void> {
    harness = await RouterTestingHarness.create();
    component = await harness.navigateByUrl(
      '/physio/patients/15/treatments/30/sessions/90/edit',
      TherapySessionForm,
    );
    element = harness.routeNativeElement as HTMLElement;
    backend.expectOne('/api/patients/15').flush(fakePatient(15));
    backend.expectOne('/api/treatments/30').flush(fakeTreatment(30));
    backend.expectOne('/api/therapy-sessions/90').flush(
      fakeSession(90, {
        sequenceNumber: 2,
        responsible: {
          id: responsibleId,
          fullName: responsibleId === 7 ? 'Lcda. Valeria Andrade' : 'Lcdo. Mateo Ríos',
        },
      }),
    );
    await settle();
  }

  async function submit(): Promise<void> {
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await settle();
  }

  function fill(): void {
    form().patchValue({
      sessionType: ' Fisioterapia lumbar mecánica ',
      sessionDate: '2026-11-04',
      sessionTime: '22:59',
      painScore: 4,
      status: 'COMPLETED',
      progressStatus: 'IMPROVING',
      activitiesPerformed: 'Movilización lumbar',
      clinicalObservations: 'Buena tolerancia',
      patientInstructions: '   ',
    });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'physio/patients/:patientId/treatments/:treatmentId/sessions/new',
            component: TherapySessionForm,
          },
          {
            path: 'physio/patients/:patientId/treatments/:treatmentId/sessions/:sessionId/edit',
            component: TherapySessionForm,
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

  it('shows the patient, the next session number and the responsible as read-only', async () => {
    await openNew();

    expect(element.querySelector('h1')?.textContent).toContain('Registrar Sesión Terapéutica');
    expect(readonlyValues()).toEqual([
      'Juan Carlos Pérez Gómez (1710034065) - Lumbago con ciática',
      'Sesión N.º 4',
      'Lcda. Valeria Andrade',
    ]);
    expect(element.textContent).toContain('Lo asigna el sistema al guardar.');
    expect(submitButton()?.textContent).toContain('Registrar sesión');
  });

  it('validates the required fields in Spanish and sends nothing', async () => {
    await openNew();
    form().controls.sessionType.setValue('   ');

    await submit();

    expect(errors()).toEqual(
      expect.arrayContaining([
        'Ingrese el tipo de sesión.',
        'Seleccione la EVA de la sesión.',
        'Seleccione el estado de evolución.',
        'Describa las actividades realizadas.',
        'Ingrese las observaciones clínicas.',
      ]),
    );
    expect(element.textContent).toContain('Revise los campos marcados antes de guardar.');

    // Once the fields are fixed, the notice goes away
    fill();
    await settle();
    expect(element.textContent).not.toContain('Revise los campos marcados antes de guardar.');
  });

  it('does not accept a date after today', async () => {
    await openNew();
    fill();
    form().controls.sessionDate.setValue('2999-01-01');

    await submit();

    expect(errors()).toContain('La fecha no puede ser posterior a hoy.');
  });

  it('registers the session without a responsible nor a number and opens the list', async () => {
    await openNew();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fill();

    await submit();

    const request = backend.expectOne('/api/treatments/30/therapy-sessions');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      sessionDate: '2026-11-04',
      sessionTime: '22:59',
      sessionType: 'Fisioterapia lumbar mecánica',
      activitiesPerformed: 'Movilización lumbar',
      clinicalObservations: 'Buena tolerancia',
      patientInstructions: null,
      painScore: 4,
      progressStatus: 'IMPROVING',
      status: 'COMPLETED',
    });
    request.flush(fakeSession(95, { sequenceNumber: 4 }));
    await settle();

    expect(navigate).toHaveBeenCalledWith('/physio/patients/15/treatments/30/sessions');
  });

  it('shows the message of the server when the session is rejected', async () => {
    await openNew();
    fill();

    await submit();
    backend
      .expectOne('/api/treatments/30/therapy-sessions')
      .flush(
        { status: 422, detail: 'Solo se pueden registrar sesiones en un tratamiento activo.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await settle();

    expect(element.textContent).toContain(
      'Solo se pueden registrar sesiones en un tratamiento activo.',
    );
    expect(submitButton()?.disabled).toBe(false);
  });

  it('does not offer to register a session on a closed treatment', async () => {
    await openNew('COMPLETED');

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'El tratamiento no está activo',
    );
    expect(submitButton()).toBeUndefined();
    expect(form().disabled).toBe(true);
  });

  it('lets the author correct the session, keeping its number and responsible', async () => {
    await openEdit();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    expect(element.querySelector('h1')?.textContent).toContain('Corregir Sesión Terapéutica');
    expect(readonlyValues()).toEqual([
      'Juan Carlos Pérez Gómez (1710034065) - Lumbago con ciática',
      'Sesión N.º 2',
      'Lcda. Valeria Andrade',
    ]);
    expect(form().getRawValue()).toEqual(
      expect.objectContaining({
        sessionTime: '09:30',
        painScore: 4,
        activitiesPerformed: 'Movilización lumbar y TENS 15 minutos',
      }),
    );

    form().controls.painScore.setValue(3);
    await submit();

    const request = backend.expectOne('/api/therapy-sessions/90');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(
      expect.objectContaining({ painScore: 3, sessionTime: '09:30' }),
    );
    expect(request.request.body).not.toHaveProperty('responsibleId');
    expect(request.request.body).not.toHaveProperty('sequenceNumber');
    request.flush(fakeSession(90, { sequenceNumber: 2, painScore: 3 }));
    await settle();
  });

  it('shows the session of another physiotherapist read-only', async () => {
    await openEdit(8);

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Solo el fisioterapeuta que registró la sesión puede corregirla.',
    );
    expect(readonlyValues()).toContain('Lcdo. Mateo Ríos');
    expect(form().disabled).toBe(true);
    expect(submitButton()).toBeUndefined();
  });

  it('does not show a session of another treatment', async () => {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/physio/patients/15/treatments/30/sessions/90/edit');
    element = harness.routeNativeElement as HTMLElement;
    backend.expectOne('/api/patients/15').flush(fakePatient(15));
    backend.expectOne('/api/treatments/30').flush(fakeTreatment(30));
    backend.expectOne('/api/therapy-sessions/90').flush(fakeSession(90, { treatmentId: 31 }));
    await settle();

    expect(element.textContent).toContain('La sesión no pertenece a este tratamiento.');
    expect(element.querySelector('form')).toBeNull();
  });
});
