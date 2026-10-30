import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PatientApi, RegisterPatientRequest } from './patient-api';

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

  it('looks a patient up by id', () => {
    api.findById(12).subscribe();

    expect(backend.expectOne('/api/patients/12').request.method).toBe('GET');
  });
});
