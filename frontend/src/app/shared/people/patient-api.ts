import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../core/http/page';
import { AddressValue } from '../forms/location-catalog';
import { PersonAccount } from './people-api';

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
  /** Mobile 09XXXXXXXX or landline with its province code (MEJ-03). */
  phone: string;
  email: string;
  /** Structured address (MEJ-03): codes of INEC's catalog and the streets. */
  address: AddressValue;
  occupation: string;
  educationLevel: string;
  /** "¿Tiene seguro de salud?" (MEJ-03). */
  hasHealthInsurance: boolean;
  ethnicSelfIdentification: string | null;
  religion: string | null;
  /** MEJ-06: version in force of the consent to process the data, signed by the patient (required). */
  dataProcessingConsentVersion: string;
  /** MEJ-06: version of the consent to email communications, or null when the patient did not sign it. */
  emailCommunicationsConsentVersion: string | null;
}

/** The personal data of the form, without the consents (which only go with the registration). */
type PersonalDataRequest = Omit<
  RegisterPatientRequest,
  'dataProcessingConsentVersion' | 'emailCommunicationsConsentVersion'
>;

/** Address of a patient as the API returns it, with the place names and the composed text (MEJ-03). */
export interface PatientAddress {
  /** Null while the address of a patient registered before MEJ-03 is incomplete. */
  provinceCode: string | null;
  provinceName: string | null;
  cantonCode: string | null;
  cantonName: string | null;
  parishCode: string | null;
  parishName: string | null;
  mainStreet: string | null;
  streetNumber: string | null;
  crossStreet: string | null;
  /** For a patient registered before MEJ-03, its former free-text address. */
  reference: string | null;
  formatted: string;
  complete: boolean;
}

/** Patient as returned by the API; never includes clinical data. */
export interface Patient extends Omit<PersonalDataRequest, 'address' | 'phone' | 'email'> {
  id: number;
  /** Null only after an accepted erasure request (MEJ-06). */
  phone: string | null;
  email: string | null;
  address: PatientAddress;
  /** "Datos por completar": incomplete address or phone outside the rule (MEJ-03). */
  needsCompletion: boolean;
  /** Equal to the national ID. */
  medicalRecordNumber: string;
  /** Portal account (MEJ-02), created from the record; null while the patient has none. */
  portalAccount: PersonAccount | null;
  /** Completed years, calculated by the server from the birth date. */
  age: number;
  createdAt: string;
  /** Last update of the data (HU-06), or null if it was never updated. */
  updatedAt: string | null;
  /** MEJ-06: "Consentimiento pendiente": no valid consent to process the data, so no new care nor portal. */
  consentPending: boolean;
  /** MEJ-06: blocked by an accepted erasure request: nobody edits it and its contact data was cleared. */
  blocked: boolean;
  blockedAt: string | null;
}

/** Body of PUT /api/patients/{id}: the national ID and the medical record number never change. */
export type UpdatePatientRequest = Omit<PersonalDataRequest, 'nationalId'>;

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
  /** "Datos por completar" (MEJ-03). */
  needsCompletion: boolean;
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

  /**
   * POST /api/patients/{id}/portal-account (MEJ-02): creates the portal account with the patient's email and names
   * (nothing is typed) and emails the activation link.
   */
  createPortalAccount(id: number): Observable<PersonAccount> {
    return this.http.post<PersonAccount>(`/api/patients/${id}/portal-account`, null);
  }
}
