import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

/** Days of the clinic: Monday to Saturday (MEJ-04). */
export type WorkDay = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY';

/** One slot of a weekly schedule; the hours as HH:mm. */
export interface ScheduleSlot {
  day: WorkDay;
  start: string;
  end: string;
}

/** The weekly schedule of a physiotherapist, with the clinic hours that bound it. */
export interface StaffSchedule {
  staffId: number;
  fullName: string;
  /** HH:mm */
  openingTime: string;
  /** HH:mm */
  closingTime: string;
  /** Ordered by day and start time, with the contiguous slots merged. */
  slots: ScheduleSlot[];
}

/**
 * Weekly schedules (MEJ-04): the administrator and the operations director read and replace a physiotherapist's;
 * the physiotherapist reads its own.
 */
@Injectable({ providedIn: 'root' })
export class ScheduleApi {
  private readonly http = inject(HttpClient);

  ofStaff(staffId: number): Observable<StaffSchedule> {
    return this.http.get<StaffSchedule>(`/api/staff/${staffId}/schedule`);
  }

  /** Replaces the whole week; the response has the contiguous slots merged. */
  replace(staffId: number, slots: ScheduleSlot[]): Observable<StaffSchedule> {
    return this.http.put<StaffSchedule>(`/api/staff/${staffId}/schedule`, { slots });
  }

  /** "Mi horario": the schedule of the authenticated physiotherapist. */
  own(): Observable<StaffSchedule> {
    return this.http.get<StaffSchedule>('/api/physio/schedule');
  }
}
