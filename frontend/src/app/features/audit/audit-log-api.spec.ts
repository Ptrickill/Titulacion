import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuditLogApi, NO_FILTERS } from './audit-log-api';

describe('AuditLogApi', () => {
  let api: AuditLogApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AuditLogApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('searches with the page and only the filters that have a value', () => {
    api.search({ ...NO_FILTERS, userId: 7, action: 'READ', from: '2026-10-01' }, 2, 20).subscribe();

    const request = backend.expectOne((r) => r.url === '/api/audit-logs');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys().sort()).toEqual([
      'action',
      'from',
      'page',
      'size',
      'userId',
    ]);
    expect(request.request.params.get('userId')).toBe('7');
    expect(request.request.params.get('action')).toBe('READ');
    expect(request.request.params.get('from')).toBe('2026-10-01');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('20');
  });

  it('reads the entity types and the users for the filters', () => {
    api.entityTypes().subscribe();
    api.users().subscribe();

    expect(backend.expectOne('/api/audit-logs/entity-types').request.method).toBe('GET');
    expect(backend.expectOne('/api/audit-logs/users').request.method).toBe('GET');
  });
});
