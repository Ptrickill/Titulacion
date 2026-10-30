import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type Sex = 'MALE' | 'FEMALE';

/** Body of POST /api/patients: admission and personal data only (the server assigns the record number). */
export interface RegisterPatientRequest {
  nationalId: string;
  /** ISO date (yyyy-MM-dd). */
  admissionDate: string;
  firstName: string;
  lastName: string;
  /** ISO date (yyyy-MM-dd). */
  birthDate: string;
  sex: Sex;
  phone: string;
  email: string;
  address: string;
  occupation: string;
  educationLevel: string;
  healthInsurance: string;
  ethnicSelfIdentification: string | null;
  religion: string | null;
}

/** Patient as returned by the API; never includes clinical data. */
export interface Patient extends RegisterPatientRequest {
  id: number;
  /** Equal to the national ID. */
  medicalRecordNumber: string;
  /** Completed years, calculated by the server from the birth date. */
  age: number;
  createdAt: string;
}

/** Property of the 409 problem response with the id of the patient that has the national ID. */
export const EXISTING_PATIENT_ID = 'existingPatientId';

@Injectable({ providedIn: 'root' })
export class PatientApi {
  private readonly http = inject(HttpClient);

  register(request: RegisterPatientRequest): Observable<Patient> {
    return this.http.post<Patient>('/api/patients', request);
  }

  findById(id: number): Observable<Patient> {
    return this.http.get<Patient>(`/api/patients/${id}`);
  }
}
