import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../core/http/page';
import { AddressValue } from '../forms/location-catalog';
import { PatientAddress, Sex } from './patient-api';
import { PersonAccount, PersonSummary, StaffRole } from './people-api';
import { AccountStatus } from './user-api';

/** Body of PUT /api/staff/{id}: the role is not edited here. */
export interface UpdateStaffRequest {
  nationalId: string;
  firstName: string;
  lastName: string;
  /** ISO date (yyyy-MM-dd). */
  birthDate: string;
  sex: Sex;
  phone: string;
  email: string;
  address: AddressValue;
}

/** Body of POST /api/staff: the record and the role of its account, created together (MEJ-02). */
export interface RegisterStaffRequest extends UpdateStaffRequest {
  role: StaffRole;
}

/** A staff member as the API returns it; a record still to be completed may lack some data. */
export interface Staff {
  id: number;
  nationalId: string | null;
  firstName: string;
  lastName: string;
  birthDate: string | null;
  age: number | null;
  sex: Sex | null;
  phone: string | null;
  email: string;
  address: PatientAddress;
  role: StaffRole;
  account: PersonAccount | null;
  /** "Datos por completar". */
  needsCompletion: boolean;
  createdAt: string;
  updatedAt: string | null;
}

/** Filters of the operations director's staff list (MEJ-05): every staff member has an account. */
export interface StaffFilter {
  query: string;
  role: StaffRole | null;
  accountStatus: AccountStatus | null;
}

/** Indicators of "Gestión de Personal" (MEJ-05). */
export interface StaffStats {
  activeAccounts: number;
  physiotherapists: number;
  receptionists: number;
  directors: number;
}

/** Staff records and the staff list (MEJ-05: the operations director only). */
@Injectable({ providedIn: 'root' })
export class StaffApi {
  private readonly http = inject(HttpClient);

  /** GET /api/staff: the staff by last name, with the status of each account. */
  list(filter: StaffFilter, page: number, size: number): Observable<Page<PersonSummary>> {
    let params = new HttpParams().set('q', filter.query).set('page', page).set('size', size);
    if (filter.role) {
      params = params.set('role', filter.role);
    }
    if (filter.accountStatus) {
      params = params.set('accountStatus', filter.accountStatus);
    }
    return this.http.get<Page<PersonSummary>>('/api/staff', { params });
  }

  /** GET /api/staff/stats: the four indicators of the staff. */
  stats(): Observable<StaffStats> {
    return this.http.get<StaffStats>('/api/staff/stats');
  }

  register(request: RegisterStaffRequest): Observable<Staff> {
    return this.http.post<Staff>('/api/staff', request);
  }

  findById(id: number): Observable<Staff> {
    return this.http.get<Staff>(`/api/staff/${id}`);
  }

  update(id: number, request: UpdateStaffRequest): Observable<Staff> {
    return this.http.put<Staff>(`/api/staff/${id}`, request);
  }
}
