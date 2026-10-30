import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../../core/http/page';

export type Severity = 'MILD' | 'MODERATE' | 'SEVERE';
export type TreatmentStatus = 'ACTIVE' | 'COMPLETED' | 'ABANDONED';

/** ICD-10 catalog entry (GET /api/icd10-diagnoses). */
export interface Icd10Diagnosis {
  code: string;
  description: string;
}

/** Diagnostic information, initial pain assessment and therapy plan of a treatment. */
export interface TreatmentRequest {
  diagnosisCode: string;
  chiefComplaint: string;
  currentIllness: string;
  referringPhysician: string;
  complementaryTests: string;
  severity: Severity;
  painType: string;
  painLocation: string;
  /** EVA, 0 to 10. */
  initialPainScore: number;
  painFrequency: string;
  sessionFrequency: string;
  goal: string;
  /** Greater than 0. */
  plannedSessions: number;
}

/** Body of POST /api/patients/{patientId}/clinical-record. */
export interface CreateClinicalRecordRequest {
  habits: string;
  physicalActivity: string;
  allergies: string;
  currentMedication: string;
  personalHistory: string;
  surgicalHistory: string;
  familyHistory: string;
  treatment: TreatmentRequest;
}

export interface Treatment extends Omit<TreatmentRequest, 'diagnosisCode'> {
  id: number;
  clinicalRecordId: number;
  patientId: number;
  diagnosis: Icd10Diagnosis;
  status: TreatmentStatus;
  startDate: string;
  responsiblePhysiotherapistId: number | null;
  createdAt: string;
}

export interface ClinicalRecord extends Omit<CreateClinicalRecordRequest, 'treatment'> {
  id: number;
  patientId: number;
  createdAt: string;
  treatments: Treatment[];
}

/** Current treatment of a patient: the active one, otherwise the most recent. */
export interface CurrentTreatment {
  id: number;
  diagnosisCode: string;
  diagnosisDescription: string;
  severity: Severity;
  plannedSessions: number;
  status: TreatmentStatus;
}

/** Row of the physiotherapist's patient list (GET /api/physio/patients). */
export interface PhysioPatient {
  id: number;
  nationalId: string;
  medicalRecordNumber: string;
  firstName: string;
  lastName: string;
  age: number;
  clinicalRecordId: number | null;
  currentTreatment: CurrentTreatment | null;
}

/** Property of the 409 problem response with the id of the record the patient already has. */
export const EXISTING_CLINICAL_RECORD_ID = 'existingClinicalRecordId';

@Injectable({ providedIn: 'root' })
export class ClinicalRecordApi {
  private readonly http = inject(HttpClient);

  searchDiagnoses(query: string): Observable<Icd10Diagnosis[]> {
    return this.http.get<Icd10Diagnosis[]>('/api/icd10-diagnoses', {
      params: new HttpParams().set('q', query),
    });
  }

  listPatients(query: string, page: number, size: number): Observable<Page<PhysioPatient>> {
    return this.http.get<Page<PhysioPatient>>('/api/physio/patients', {
      params: new HttpParams().set('q', query).set('page', page).set('size', size),
    });
  }

  createRecord(
    patientId: number,
    request: CreateClinicalRecordRequest,
  ): Observable<ClinicalRecord> {
    return this.http.post<ClinicalRecord>(`/api/patients/${patientId}/clinical-record`, request);
  }

  openTreatment(clinicalRecordId: number, request: TreatmentRequest): Observable<Treatment> {
    return this.http.post<Treatment>(
      `/api/clinical-records/${clinicalRecordId}/treatments`,
      request,
    );
  }
}
