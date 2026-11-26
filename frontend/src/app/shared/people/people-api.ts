import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Role } from '../../core/auth/role';
import { Page } from '../../core/http/page';
import { AccountStatus } from './user-api';

/** Who a person is in the registry (MEJ-02). */
export type PersonType = 'PATIENT' | 'STAFF';

/** Roles of the staff: every role except PATIENT. */
export type StaffRole = Exclude<Role, 'PATIENT'>;

/** Status of a person's account in the registry: NONE when the person has no account yet. */
export type PersonAccountStatus = 'NONE' | AccountStatus;

/** A row of GET /api/people: identification, contact and the status of the account. */
export interface PersonSummary {
  type: PersonType;
  /** Id of the patient or of the staff record. */
  id: number;
  /** Null only for a staff record still to be completed. */
  nationalId: string | null;
  firstName: string;
  lastName: string;
  /** PATIENT for the patients; the account's role for the staff. */
  role: Role;
  email: string;
  phone: string | null;
  accountStatus: PersonAccountStatus;
  userId: number | null;
  emailChangePending: boolean;
  /** "Datos por completar". */
  needsCompletion: boolean;
  /** ISO-8601 instant of the last login, or null without account or if it never logged in. */
  lastLoginAt: string | null;
}

/** A registered person that already has the national ID or the email looked up. */
export interface PersonMatch {
  type: PersonType;
  id: number;
  fullName: string;
  field: 'nationalId' | 'email';
}

/** The account of a person (in a patient's or a staff member's record): never its email nor its password. */
export interface PersonAccount {
  userId: number;
  status: AccountStatus;
  emailChangePending: boolean;
  /** ISO-8601 instant of the last login, or null if it never logged in. */
  lastLoginAt: string | null;
}

/** Filters of the reception's patient registry (MEJ-05). */
export interface PatientRegistryFilter {
  query: string;
  /** Status of the portal account, or NONE for the patients without one. */
  accountStatus: PersonAccountStatus | null;
}

/** Indicators of "Registro de Pacientes" (MEJ-05): the patients and their portal access. */
export interface PatientRegistryStats {
  patients: number;
  portalActive: number;
  portalPending: number;
  withoutPortal: number;
}

/** Properties of the 409 problem response with the existing record (MEJ-02). */
export const EXISTING_STAFF_ID = 'existingStaffId';
export const CONFLICT_FIELD = 'field';

/**
 * Route of the record of a person (MEJ-05): the reception opens the patients and the operations director the
 * staff, so each side only links to its own records.
 */
export function personLink(type: PersonType, id: number): (string | number)[] {
  return type === 'PATIENT' ? ['/admin/patients', id] : ['/director/staff', id];
}

/** The reception's patient registry (MEJ-05) and the national ID and email lookup of both forms. */
@Injectable({ providedIn: 'root' })
export class PeopleApi {
  private readonly http = inject(HttpClient);

  /** GET /api/people: the patients by last name, with their portal account; never the staff. */
  list(filter: PatientRegistryFilter, page: number, size: number): Observable<Page<PersonSummary>> {
    let params = new HttpParams().set('q', filter.query).set('page', page).set('size', size);
    if (filter.accountStatus) {
      params = params.set('accountStatus', filter.accountStatus);
    }
    return this.http.get<Page<PersonSummary>>('/api/people', { params });
  }

  /** GET /api/people/stats: the four indicators of the patient registry. */
  stats(): Observable<PatientRegistryStats> {
    return this.http.get<PatientRegistryStats>('/api/people/stats');
  }

  /** Patients and staff that already have the national ID or the email. */
  matches(lookup: { nationalId?: string; email?: string }): Observable<PersonMatch[]> {
    let params = new HttpParams();
    if (lookup.nationalId) {
      params = params.set('nationalId', lookup.nationalId);
    }
    if (lookup.email) {
      params = params.set('email', lookup.email);
    }
    return this.http.get<PersonMatch[]>('/api/people/matches', { params });
  }
}
