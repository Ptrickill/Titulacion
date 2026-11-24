import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AddressValue } from '../../../shared/forms/location-catalog';
import { PatientAddress, Sex } from '../patients/patient-api';
import { PersonAccount, StaffRole } from './people-api';

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

/** Staff records (administrator only). */
@Injectable({ providedIn: 'root' })
export class StaffApi {
  private readonly http = inject(HttpClient);

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
