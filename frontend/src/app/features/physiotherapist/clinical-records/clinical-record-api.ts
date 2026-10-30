import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../../core/http/page';
import { Sex } from '../../admin/patients/patient-api';
import type { ResponsiblePeriod } from './treatment-api';

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

/** Body of PUT /api/patients/{patientId}/clinical-record (HU-09): the treatments are not touched. */
export type ClinicalHistoryRequest = Omit<CreateClinicalRecordRequest, 'treatment'>;

/** Habits and medical history of a clinical record. */
export interface ClinicalHistory extends ClinicalHistoryRequest {
  id: number;
  patientId: number;
  createdAt: string;
  /** Last edit of the habits and medical history, or null if they were never edited. */
  updatedAt: string | null;
}

/** Personal and contact data of the patient in the clinical record view. */
export interface PatientProfile {
  id: number;
  nationalId: string;
  medicalRecordNumber: string;
  firstName: string;
  lastName: string;
  birthDate: string;
  /** Completed years, calculated by the server. */
  age: number;
  sex: Sex;
  phone: string;
  email: string;
  address: string;
  occupation: string;
  educationLevel: string;
  healthInsurance: string;
  admissionDate: string;
}

/** The active treatment of the record view, with its current responsible. */
export interface ActiveTreatment extends Omit<TreatmentRequest, 'diagnosisCode'> {
  id: number;
  diagnosis: Icd10Diagnosis;
  status: TreatmentStatus;
  startDate: string;
  currentResponsible: ResponsiblePeriod | null;
}

/** A completed or abandoned treatment of the record view. */
export interface PreviousTreatment {
  id: number;
  diagnosis: Icd10Diagnosis;
  severity: Severity;
  status: TreatmentStatus;
  startDate: string;
  plannedSessions: number;
  /** Last change of the treatment (e.g. when it was closed), or null. */
  updatedAt: string | null;
  /** Last responsible physiotherapist, or null. */
  responsible: ResponsiblePeriod | null;
}

/** GET /api/patients/{patientId}/clinical-record (HU-09): the full record, read before a session. */
export interface PatientClinicalRecord {
  patient: PatientProfile;
  clinicalRecord: ClinicalHistory;
  activeTreatment: ActiveTreatment | null;
  /** Most recent first. */
  previousTreatments: PreviousTreatment[];
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

  /** The full record of the patient; every read is audited by the server. */
  findRecord(patientId: number): Observable<PatientClinicalRecord> {
    return this.http.get<PatientClinicalRecord>(`/api/patients/${patientId}/clinical-record`);
  }

  updateHistory(patientId: number, request: ClinicalHistoryRequest): Observable<ClinicalHistory> {
    return this.http.put<ClinicalHistory>(`/api/patients/${patientId}/clinical-record`, request);
  }

  openTreatment(clinicalRecordId: number, request: TreatmentRequest): Observable<Treatment> {
    return this.http.post<Treatment>(
      `/api/clinical-records/${clinicalRecordId}/treatments`,
      request,
    );
  }
}
