import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PortalApi } from './portal-api';
import { fakeProgress, fakeTreatment } from './portal-testing';

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
});
