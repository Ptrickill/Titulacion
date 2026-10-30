import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TreatmentApi } from './treatment-api';
import { fakeHistory, fakeTreatment } from './treatment-testing';

describe('TreatmentApi', () => {
  let api: TreatmentApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(TreatmentApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('reads the treatment and its history', () => {
    api.find(30).subscribe((treatment) => expect(treatment.goal).toContain('Reducción'));
    api.history(30).subscribe((history) => expect(history.status).toBe('ACTIVE'));

    backend.expectOne({ method: 'GET', url: '/api/treatments/30' }).flush(fakeTreatment(30));
    backend.expectOne({ method: 'GET', url: '/api/treatments/30/history' }).flush(fakeHistory(30));
  });

  it('changes the responsible with PUT and the status with PATCH', () => {
    api.changeResponsible(30, 8).subscribe();
    api.changeStatus(30, { newStatus: 'COMPLETED', reason: 'Alta' }).subscribe();

    const responsible = backend.expectOne('/api/treatments/30/responsible');
    expect(responsible.request.method).toBe('PUT');
    expect(responsible.request.body).toEqual({ physiotherapistId: 8 });
    responsible.flush(fakeHistory(30));
    const status = backend.expectOne('/api/treatments/30/status');
    expect(status.request.method).toBe('PATCH');
    expect(status.request.body).toEqual({ newStatus: 'COMPLETED', reason: 'Alta' });
    status.flush(fakeHistory(30, { status: 'COMPLETED', allowedNextStatuses: [] }));
  });

  it('lists the active physiotherapists', () => {
    api.listPhysiotherapists().subscribe((list) => expect(list).toHaveLength(1));

    backend
      .expectOne({ method: 'GET', url: '/api/physio/physiotherapists' })
      .flush([{ id: 8, fullName: 'Lcdo. Mateo Ríos' }]);
  });
});
