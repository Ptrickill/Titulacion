import { TestBed } from '@angular/core/testing';
import { DateAdapter } from '@angular/material/core';
import {
  EC_DATE_INPUT,
  EcDateAdapter,
  formatIsoDate,
  fromIsoDate,
  provideEcDates,
  toIsoDate,
} from './ec-date-adapter';

describe('EcDateAdapter (MEJ-03)', () => {
  let adapter: EcDateAdapter;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideEcDates() });
    adapter = TestBed.inject(DateAdapter) as EcDateAdapter;
  });

  const parse = (text: string) => adapter.parse(text, EC_DATE_INPUT);

  it('reads dd/mm/aaaa, with or without leading zeros', () => {
    expect(toIsoDate(parse('15/03/1996'))).toBe('1996-03-15');
    expect(toIsoDate(parse('5/3/1996'))).toBe('1996-03-05');
    expect(toIsoDate(parse(' 29/02/2024 '))).toBe('2024-02-29');
    expect(parse('')).toBeNull();
  });

  it('rejects dates that do not exist instead of correcting them', () => {
    for (const text of [
      '31/02/2026',
      '29/02/2025',
      '31/04/2026',
      '00/01/2026',
      '15/13/2026',
      '32/01/2026',
    ]) {
      const date = parse(text);
      expect(date, text).not.toBeNull();
      expect(adapter.isValid(date!), text).toBe(false);
    }
  });

  it('rejects two-digit years and other formats', () => {
    for (const text of [
      '15/03/96',
      '1/2/26',
      '1996-03-15',
      '15-03-1996',
      '15/03/1996x',
      'mañana',
    ]) {
      expect(adapter.isValid(parse(text)!), text).toBe(false);
    }
  });

  it('writes dd/mm/aaaa and starts the week on Monday', () => {
    expect(adapter.format(new Date(2026, 0, 5), EC_DATE_INPUT)).toBe('05/01/2026');
    expect(adapter.getFirstDayOfWeek()).toBe(1);
  });

  it('converts to and from the API format yyyy-MM-dd', () => {
    expect(toIsoDate(fromIsoDate('2026-11-22'))).toBe('2026-11-22');
    expect(fromIsoDate('22/11/2026')).toBeNull();
    expect(toIsoDate(null)).toBe('');
    expect(formatIsoDate('1996-03-15')).toBe('15/03/1996');
    expect(formatIsoDate('')).toBe('');
  });
});
