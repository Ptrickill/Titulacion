import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PatientApi, RegisterPatientRequest, UpdatePatientRequest } from './patient-api';

describe('PatientApi', () => {
  let api: PatientApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PatientApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('registers a patient', () => {
    const body = { nationalId: '1710034065' } as RegisterPatientRequest;
    api.register(body).subscribe();

    const request = backend.expectOne('/api/patients');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
  });

  it('searches patients with the query and the page', () => {
    api.search('perez', 2, 10).subscribe();

    const request = backend.expectOne((r) => r.url === '/api/patients');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('q')).toBe('perez');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('10');
  });

  it('updates a patient', () => {
    const body = { firstName: 'Juan' } as UpdatePatientRequest;
    api.update(12, body).subscribe();

    const request = backend.expectOne('/api/patients/12');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);
  });

  it('looks a patient up by id', () => {
    api.findById(12).subscribe();

    expect(backend.expectOne('/api/patients/12').request.method).toBe('GET');
  });
});
