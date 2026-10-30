import { HttpClient, HttpParams } from '@angular/common/http';
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
  /** Last update of the data (HU-06), or null if it was never updated. */
  updatedAt: string | null;
}

/** Body of PUT /api/patients/{id}: the national ID and the medical record number never change. */
export type UpdatePatientRequest = Omit<RegisterPatientRequest, 'nationalId'>;

/** Search result row (GET /api/patients): identification and contact data only. */
export interface PatientSummary {
  id: number;
  nationalId: string;
  medicalRecordNumber: string;
  firstName: string;
  lastName: string;
  age: number;
  phone: string;
  email: string;
}

/** One page of results, as the backend's PageResponse. */
export interface Page<T> {
  content: T[];
  /** Zero-based. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Property of the 409 problem response with the id of the patient that has the national ID. */
export const EXISTING_PATIENT_ID = 'existingPatientId';

@Injectable({ providedIn: 'root' })
export class PatientApi {
  private readonly http = inject(HttpClient);

  /** Partial search by name (accents and case ignored) or by the beginning of the national ID. */
  search(query: string, page: number, size: number): Observable<Page<PatientSummary>> {
    return this.http.get<Page<PatientSummary>>('/api/patients', {
      params: new HttpParams().set('q', query).set('page', page).set('size', size),
    });
  }

  register(request: RegisterPatientRequest): Observable<Patient> {
    return this.http.post<Patient>('/api/patients', request);
  }

  findById(id: number): Observable<Patient> {
    return this.http.get<Patient>(`/api/patients/${id}`);
  }

  update(id: number, request: UpdatePatientRequest): Observable<Patient> {
    return this.http.put<Patient>(`/api/patients/${id}`, request);
  }
}
