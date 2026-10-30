import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { fakePatient } from '../../admin/patients/patient-testing';
import { TreatmentPage } from './treatment-page';
import { fakeHistory, fakeTreatment } from './treatment-testing';

describe('TreatmentPage', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let element: HTMLElement;

  async function open(url: string): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, TreatmentPage);
    element = harness.routeNativeElement as HTMLElement;
  }

  async function settle(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'physio/patients/:patientId/treatments/:treatmentId', component: TreatmentPage },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('shows the patient card and the plan and assignment card', async () => {
    await open('/physio/patients/15/treatments/30');
    backend.expectOne('/api/treatments/30').flush(fakeTreatment(30));
    backend.expectOne('/api/patients/15').flush(fakePatient(15));
    // Renders the card, which starts the history request; whenStable would wait for the pending resource
    TestBed.tick();
    backend.expectOne('/api/treatments/30/history').flush(fakeHistory(30));
    await settle();

    expect(element.querySelector('h1')?.textContent).toContain('Plan Terapéutico y Asignación');
    expect(element.querySelector('.patient-card')?.textContent).toContain(
      'Juan Carlos Pérez Gómez',
    );
    expect(element.querySelector('app-treatment-plan-card')?.textContent).toContain(
      'Lcda. Valeria Andrade',
    );
  });

  it('does not show a treatment of another patient', async () => {
    await open('/physio/patients/16/treatments/30');
    backend.expectOne('/api/treatments/30').flush(fakeTreatment(30, { patientId: 15 }));
    backend.expectOne('/api/patients/16').flush(fakePatient(16));
    await settle();

    expect(element.querySelector('app-treatment-plan-card')).toBeNull();
    expect(element.textContent).toContain('El tratamiento no pertenece a este paciente.');
  });

  it('tells when the treatment does not exist', async () => {
    await open('/physio/patients/15/treatments/404');
    backend
      .expectOne('/api/treatments/404')
      .flush(
        { status: 404, detail: 'El tratamiento no existe.' },
        { status: 404, statusText: 'Not Found' },
      );
    backend.expectOne('/api/patients/15').flush(fakePatient(15));
    await settle();

    expect(element.textContent).toContain('No se pudo abrir el tratamiento');
    expect(element.textContent).toContain('El tratamiento no existe.');
  });
});
