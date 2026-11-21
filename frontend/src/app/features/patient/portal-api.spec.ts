import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PortalApi } from './portal-api';
import { fakeInstruction, fakeProgress, fakeSession, fakeTreatment } from './portal-testing';

describe('PortalApi', () => {
  let api: PortalApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PortalApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists the treatments of the session without sending a patient id', () => {
    api.treatments().subscribe((treatments) => expect(treatments.map((t) => t.id)).toEqual([30]));

    const call = backend.expectOne('/api/portal/treatments');
    expect(call.request.method).toBe('GET');
    expect(call.request.params.keys()).toEqual([]);
    expect(call.request.body).toBeNull();
    call.flush([fakeTreatment()]);
  });

  it('reads the progress of one treatment', () => {
    api.progress(30).subscribe((progress) => expect(progress.percentage).toBe(33));

    const call = backend.expectOne('/api/portal/treatments/30/progress');
    expect(call.request.method).toBe('GET');
    call.flush(fakeProgress());
  });

  it('lists the sessions of one treatment', () => {
    api
      .sessions(30)
      .subscribe((sessions) => expect(sessions.map((s) => s.sequenceNumber)).toEqual([1, 2]));

    const call = backend.expectOne('/api/portal/treatments/30/sessions');
    expect(call.request.method).toBe('GET');
    expect(call.request.params.keys()).toEqual([]);
    call.flush([fakeSession(), fakeSession({ sequenceNumber: 2 })]);
  });

  it('reads the last instructions of one treatment', () => {
    api.instructions(30).subscribe((instruction) => expect(instruction?.sequenceNumber).toBe(4));

    const call = backend.expectOne('/api/portal/treatments/30/instructions');
    expect(call.request.method).toBe('GET');
    call.flush(fakeInstruction());
  });

  it('gives null when the treatment has no instructions (200 without a body)', () => {
    let result: unknown = 'pending';
    api.instructions(30).subscribe((instruction) => (result = instruction));

    backend.expectOne('/api/portal/treatments/30/instructions').flush(null);

    expect(result).toBeNull();
  });
});
