import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ClinicalRecordApi,
  CreateClinicalRecordRequest,
  TreatmentRequest,
} from './clinical-record-api';

describe('ClinicalRecordApi', () => {
  let api: ClinicalRecordApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ClinicalRecordApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('searches the ICD-10 catalog', () => {
    api.searchDiagnoses('lumbago').subscribe();

    const request = backend.expectOne((r) => r.url === '/api/icd10-diagnoses');
    expect(request.request.params.get('q')).toBe('lumbago');
  });

  it('lists the physiotherapist patients with the query and the page', () => {
    api.listPatients('perez', 1, 10).subscribe();

    const request = backend.expectOne((r) => r.url === '/api/physio/patients');
    expect(request.request.params.get('q')).toBe('perez');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('10');
  });

  it('creates the clinical record of a patient', () => {
    const body = { habits: 'No fuma' } as CreateClinicalRecordRequest;
    api.createRecord(15, body).subscribe();

    const request = backend.expectOne('/api/patients/15/clinical-record');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
  });

  it('opens a treatment on an existing record', () => {
    const body = { diagnosisCode: 'M54.5' } as TreatmentRequest;
    api.openTreatment(40, body).subscribe();

    const request = backend.expectOne('/api/clinical-records/40/treatments');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
  });
});
