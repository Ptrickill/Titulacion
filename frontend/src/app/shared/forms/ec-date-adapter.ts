import { Injectable, Provider } from '@angular/core';
import {
  DateAdapter,
  MAT_DATE_FORMATS,
  MAT_DATE_LOCALE,
  MatDateFormats,
  NativeDateAdapter,
} from '@angular/material/core';

/** The way dates are typed and shown in the forms (MEJ-03): day, month and four-digit year. */
export const EC_DATE_INPUT = 'dd/MM/yyyy';

/** Hours as the schedules show them (MEJ-04): 24-hour clock, two digits, e.g. 08:00 and 14:30. */
const EC_TIME: Intl.DateTimeFormatOptions = {
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
};

export const EC_DATE_FORMATS: MatDateFormats = {
  parse: { dateInput: EC_DATE_INPUT, timeInput: EC_TIME },
  display: {
    dateInput: EC_DATE_INPUT,
    monthYearLabel: { year: 'numeric', month: 'short' },
    dateA11yLabel: { year: 'numeric', month: 'long', day: 'numeric' },
    monthYearA11yLabel: { year: 'numeric', month: 'long' },
    // MEJ-04: mat-timepicker needs them
    timeInput: EC_TIME,
    timeOptionLabel: EC_TIME,
  },
};

const TYPED_DATE = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/;

/**
 * Date adapter of the forms (MEJ-03): reads and writes dd/mm/aaaa, strictly. A date that does not exist
 * (31/02/2026, 29/02 of a non-leap year) or a two-digit year is invalid, never corrected silently: the native
 * adapter would turn 31/02 into 03/03 and "1/2/26" into 1926. The calendar texts use the es-EC locale.
 */
@Injectable()
export class EcDateAdapter extends NativeDateAdapter {
  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (value instanceof Date) {
      return value;
    }
    if (typeof value !== 'string') {
      return value == null ? null : this.invalid();
    }
    const text = value.trim();
    if (!text) {
      return null;
    }
    const match = TYPED_DATE.exec(text);
    if (!match) {
      return this.invalid();
    }
    const [day, month, year] = [Number(match[1]), Number(match[2]), Number(match[3])];
    const date = new Date(year, month - 1, day);
    // The Date constructor maps years 0-99 to 1900-1999; set the year again to keep it as typed
    date.setFullYear(year);
    const exists =
      date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
    return exists && year >= 1000 ? date : this.invalid();
  }

  override format(date: Date, displayFormat: object | string): string {
    if (displayFormat === EC_DATE_INPUT) {
      if (!this.isValid(date)) {
        throw Error('EcDateAdapter: cannot format an invalid date.');
      }
      const pad = (part: number) => String(part).padStart(2, '0');
      return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`;
    }
    return super.format(date, displayFormat as object);
  }

  override getFirstDayOfWeek(): number {
    return 1;
  }
}

/** Providers of the app configuration: the adapter, its formats and the es-EC locale of the calendar. */
export function provideEcDates(): Provider[] {
  return [
    { provide: DateAdapter, useClass: EcDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: EC_DATE_FORMATS },
    { provide: MAT_DATE_LOCALE, useValue: 'es-EC' },
  ];
}

/** yyyy-MM-dd (the API format) of a local date, or '' without one. */
export function toIsoDate(date: Date | null | undefined): string {
  if (!date || isNaN(date.getTime())) {
    return '';
  }
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${String(date.getFullYear()).padStart(4, '0')}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Local date of a yyyy-MM-dd value, or null. */
export function fromIsoDate(value: string | null | undefined): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '');
  if (!match) {
    return null;
  }
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  date.setFullYear(Number(match[1]));
  return date;
}

/** HH:mm (the API format of a time of day) of a local date, or '' without a valid one. */
export function toHourMinute(date: Date | null | undefined): string {
  if (!date || isNaN(date.getTime())) {
    return '';
  }
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Today at the HH:mm time of day (what mat-timepicker works with), or null. */
export function fromHourMinute(value: string | null | undefined): Date | null {
  const match = /^(\d{2}):(\d{2})/.exec(value ?? '');
  if (!match) {
    return null;
  }
  const date = new Date();
  date.setHours(Number(match[1]), Number(match[2]), 0, 0);
  return date;
}

/** dd/mm/aaaa of a yyyy-MM-dd value, for read-only texts such as the review step. */
export function formatIsoDate(value: string | null | undefined): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '');
  return match ? `${match[3]}/${match[2]}/${match[1]}` : '';
}
