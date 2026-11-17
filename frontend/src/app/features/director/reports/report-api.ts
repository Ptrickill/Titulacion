import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../../core/http/page';
import { TreatmentStatus } from '../../../shared/clinical/treatment-labels';

/**
 * Optional filters of the report. The physiotherapist filters the treatments by their current responsible and the
 * sessions by who registered them; the status filters the treatments and the sessions of the treatments in it. The
 * range (HU-15, days of Quito, both included) filters the treatments by their period (in progress some day of it) and
 * the sessions by their date.
 */
export interface ReportFilters {
  status: TreatmentStatus | null;
  physiotherapistId: number | null;
  /** yyyy-MM-dd */
  from: string | null;
  /** yyyy-MM-dd */
  to: string | null;
}

export const NO_REPORT_FILTERS: ReportFilters = {
  status: null,
  physiotherapistId: null,
  from: null,
  to: null,
};

/** "Del 01/10/2026 al 31/10/2026", "Desde el …", "Hasta el …", or null without a range (as in the PDF). */
export function periodLabel(filters: Pick<ReportFilters, 'from' | 'to'>): string | null {
  const { from, to } = filters;
  if (from && to) {
    return `Del ${dayOf(from)} al ${dayOf(to)}`;
  }
  if (from) {
    return `Desde el ${dayOf(from)}`;
  }
  return to ? `Hasta el ${dayOf(to)}` : null;
}

/** yyyy-MM-dd as dd/MM/yyyy, without going through Date (no time zone involved). */
function dayOf(date: string): string {
  const [year, month, day] = date.split('-');
  return `${day}/${month}/${year}`;
}

/** Sessions registered by one physiotherapist, whatever their status. */
export interface PhysiotherapistSessions {
  physiotherapistId: number;
  /** Null if the account no longer exists. */
  name: string | null;
  sessions: number;
}

/** GET /api/reports/summary. */
export interface ReportSummary {
  treatments: { active: number; completed: number; abandoned: number };
  sessions: { total: number; completed: number; rescheduled: number; noShow: number };
  /** From the most sessions to the fewest; only physiotherapists with at least one. */
  sessionsByPhysiotherapist: PhysiotherapistSessions[];
  /** Total sessions over the physiotherapists of the list, with one decimal; 0 without sessions. */
  averageSessionsPerPhysiotherapist: number;
}

/** A treatment of GET /api/reports/treatments: the name of the patient and nothing else of their personal data. */
export interface ReportTreatment {
  id: number;
  patientName: string;
  diagnosis: { code: string; description: string };
  /** Null if the treatment has no current responsible. */
  responsibleName: string | null;
  status: TreatmentStatus;
  /** yyyy-MM-dd */
  startDate: string;
}

/** A physiotherapist of the filter (GET /api/reports/physiotherapists), active or not. */
export interface PhysiotherapistOption {
  id: number;
  fullName: string;
}

/** Read-only operations reports (HU-14, HU-15) for the operations director. The server audits each read. */
@Injectable({ providedIn: 'root' })
export class ReportApi {
  private readonly http = inject(HttpClient);

  summary(filters: ReportFilters): Observable<ReportSummary> {
    return this.http.get<ReportSummary>('/api/reports/summary', { params: paramsOf(filters) });
  }

  treatments(
    filters: ReportFilters,
    page: number,
    size: number,
  ): Observable<Page<ReportTreatment>> {
    const params = paramsOf(filters).set('page', page).set('size', size);
    return this.http.get<Page<ReportTreatment>>('/api/reports/treatments', { params });
  }

  physiotherapists(): Observable<PhysiotherapistOption[]> {
    return this.http.get<PhysiotherapistOption[]>('/api/reports/physiotherapists');
  }

  /**
   * The PDF of the report with the same filters (HU-15), audited by the server as an export. An error arrives as a
   * Blob too: read it with `blobProblemDetailMessage`.
   */
  exportPdf(filters: ReportFilters): Observable<Blob> {
    return this.http.get('/api/reports/summary.pdf', {
      params: paramsOf(filters),
      responseType: 'blob',
    });
  }
}

/** Only the filters that were chosen. */
function paramsOf(filters: ReportFilters): HttpParams {
  let params = new HttpParams();
  if (filters.status !== null) {
    params = params.set('status', filters.status);
  }
  if (filters.physiotherapistId !== null) {
    params = params.set('physiotherapistId', filters.physiotherapistId);
  }
  if (filters.from !== null) {
    params = params.set('from', filters.from);
  }
  if (filters.to !== null) {
    params = params.set('to', filters.to);
  }
  return params;
}
