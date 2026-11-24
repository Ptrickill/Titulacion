import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Role } from '../../../core/auth/role';
import { Page } from '../../../core/http/page';
import { AccountStatus } from '../users/user-api';

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
}

export interface PeopleFilter {
  query: string;
  type: PersonType | null;
  role: StaffRole | null;
}

/** Router state that opens the registration dialog on arrival ("Crear usuario" in "Gestión de Usuarios"). */
export const OPEN_REGISTER_STATE = 'openRegister';

/** Properties of the 409 problem response with the existing record (MEJ-02). */
export const EXISTING_STAFF_ID = 'existingStaffId';
export const CONFLICT_FIELD = 'field';

/** Route of the record of a person in the registry. */
export function personLink(type: PersonType, id: number): (string | number)[] {
  return type === 'PATIENT' ? ['/admin/people/patients', id] : ['/admin/people/staff', id];
}

/** The people registry (administrator only). */
@Injectable({ providedIn: 'root' })
export class PeopleApi {
  private readonly http = inject(HttpClient);

  /** Staff first and then patients, each by last name; the search works as the patient search. */
  list(filter: PeopleFilter, page: number, size: number): Observable<Page<PersonSummary>> {
    let params = new HttpParams().set('q', filter.query).set('page', page).set('size', size);
    if (filter.type) {
      params = params.set('type', filter.type);
    }
    if (filter.role) {
      params = params.set('role', filter.role);
    }
    return this.http.get<Page<PersonSummary>>('/api/people', { params });
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
