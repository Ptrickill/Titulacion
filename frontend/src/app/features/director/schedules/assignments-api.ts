import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../../core/http/page';
import { ScheduleSlot, WorkDay } from '../../../shared/schedules/schedule-api';

/** A physiotherapist of the weekly board, active or not. */
export interface BoardPhysiotherapist {
  staffId: number;
  /** The account: the id a treatment keeps as its responsible. */
  physiotherapistId: number;
  fullName: string;
  active: boolean;
  /** Load: ACTIVE treatments it is the current responsible of. */
  activeTreatments: number;
  slots: ScheduleSlot[];
}

export interface ScheduleBoard {
  /** HH:mm */
  openingTime: string;
  /** HH:mm */
  closingTime: string;
  /** Ordered by last names and first names. */
  physiotherapists: BoardPhysiotherapist[];
}

/** An available physiotherapist, with its slots of the requested day. */
export interface AvailablePhysiotherapist {
  physiotherapistId: number;
  staffId: number;
  fullName: string;
  activeTreatments: number;
  slots: ScheduleSlot[];
}

/** An ACTIVE treatment of the director's list: no clinical data, only the patient's name. */
export interface OperationsTreatment {
  id: number;
  patientName: string;
  /** yyyy-MM-dd */
  startDate: string;
  responsible: { id: number; fullName: string } | null;
  /** The current responsible's account is deactivated: the treatment needs a new one. */
  responsibleInactive: boolean;
  /** Days of the current responsible's schedule, Monday first. */
  scheduleDays: WorkDay[];
}

/** Schedules board, availability and reassignment of the operations director (MEJ-04). */
@Injectable({ providedIn: 'root' })
export class AssignmentsApi {
  private readonly http = inject(HttpClient);

  board(): Observable<ScheduleBoard> {
    return this.http.get<ScheduleBoard>('/api/operations/schedules');
  }

  /** Active physiotherapists with one slot that covers the range, by load and then last names. */
  availability(day: WorkDay, from: string, to: string): Observable<AvailablePhysiotherapist[]> {
    return this.http.get<AvailablePhysiotherapist[]>('/api/operations/availability', {
      params: new HttpParams().set('day', day).set('from', from).set('to', to),
    });
  }

  treatments(
    page: number,
    size: number,
    inactiveResponsible: boolean,
  ): Observable<Page<OperationsTreatment>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (inactiveResponsible) {
      params = params.set('inactiveResponsible', true);
    }
    return this.http.get<Page<OperationsTreatment>>('/api/operations/treatments', { params });
  }

  reassign(treatmentId: number, physiotherapistId: number): Observable<OperationsTreatment> {
    return this.http.put<OperationsTreatment>(
      `/api/operations/treatments/${treatmentId}/responsible`,
      { physiotherapistId },
    );
  }
}
