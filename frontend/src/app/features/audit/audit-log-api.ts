import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../core/http/page';

/** Values of audit_logs.action. */
export type AuditAction =
  'CREATE' | 'READ' | 'UPDATE' | 'STATUS_CHANGE' | 'ACCESS_DENIED' | 'LOGIN';

/** Entry of GET /api/audit-logs: metadata only, never clinical data. */
export interface AuditLogEntry {
  id: number;
  /** ISO instant (UTC). */
  occurredAt: string;
  /** Null for anonymous attempts. */
  userId: number | null;
  userName: string | null;
  action: AuditAction;
  entityType: string;
  entityId: number | null;
  ipAddress: string | null;
}

/** A user that can appear in the log (GET /api/audit-logs/users). */
export interface AuditUser {
  id: number;
  fullName: string;
}

/** Optional filters; the dates are days (yyyy-MM-dd) in Quito, both included. */
export interface AuditLogFilters {
  userId: number | null;
  action: AuditAction | null;
  entityType: string | null;
  from: string | null;
  to: string | null;
}

export const NO_FILTERS: AuditLogFilters = {
  userId: null,
  action: null,
  entityType: null,
  from: null,
  to: null,
};

/** Read-only audit log (HU-18) for the administrator and the operations director. */
@Injectable({ providedIn: 'root' })
export class AuditLogApi {
  private readonly http = inject(HttpClient);

  search(filters: AuditLogFilters, page: number, size: number): Observable<Page<AuditLogEntry>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [name, value] of Object.entries(filters)) {
      if (value !== null && value !== '') {
        params = params.set(name, value as string | number);
      }
    }
    return this.http.get<Page<AuditLogEntry>>('/api/audit-logs', { params });
  }

  entityTypes(): Observable<string[]> {
    return this.http.get<string[]>('/api/audit-logs/entity-types');
  }

  users(): Observable<AuditUser[]> {
    return this.http.get<AuditUser[]>('/api/audit-logs/users');
  }
}
