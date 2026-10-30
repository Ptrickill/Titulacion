import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Page } from '../../core/http/page';
import { AuditLogEntry } from './audit-log-api';
import { AuditLogList } from './audit-log-list';

function entry(id: number, overrides: Partial<AuditLogEntry> = {}): AuditLogEntry {
  return {
    id,
    occurredAt: '2026-10-30T19:50:04Z',
    userId: 7,
    userName: 'Lcda. Valeria Andrade',
    action: 'READ',
    entityType: 'ClinicalRecord',
    entityId: 40,
    ipAddress: '190.152.10.4',
    ...overrides,
  };
}

function page(content: AuditLogEntry[], totalElements = content.length): Page<AuditLogEntry> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) };
}

describe('AuditLogList', () => {
  let fixture: ComponentFixture<AuditLogList>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const searchRequest = (): TestRequest =>
    backend.expectOne((request) => request.url === '/api/audit-logs');
  const rows = () =>
    Array.from(element.querySelectorAll('tbody tr')).map((row) =>
      row.textContent!.replace(/\s+/g, ' ').trim(),
    );
  const form = () => fixture.componentInstance['filters'];

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AuditLogList);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    backend.expectOne('/api/audit-logs/users').flush([
      { id: 7, fullName: 'Lcda. Valeria Andrade' },
      { id: 8, fullName: 'Lcdo. Mateo Ríos' },
    ]);
    backend.expectOne('/api/audit-logs/entity-types').flush(['ClinicalRecord', 'patients']);
  });

  afterEach(() => backend.verify());

  it('lists the entries with metadata only, in Quito time', async () => {
    const first = searchRequest();
    expect(first.request.params.get('page')).toBe('0');
    expect(first.request.params.get('size')).toBe('20');
    first.flush(
      page([
        entry(1),
        entry(2, {
          userId: null,
          userName: null,
          action: 'ACCESS_DENIED',
          entityType: 'patients',
          entityId: null,
          ipAddress: null,
        }),
        entry(3, { userName: null, userId: 99, action: 'STATUS_CHANGE', entityType: 'Treatment' }),
      ]),
    );
    await settle();

    expect(element.querySelector('h1')?.textContent).toContain('Auditoría');
    const headers = Array.from(element.querySelectorAll('th')).map((th) => th.textContent?.trim());
    expect(headers).toEqual(['Fecha y hora', 'Usuario', 'Acción', 'Entidad', 'IP']);
    const [read, denied, change] = rows();
    expect(read).toContain('30/10/2026 14:50:04');
    expect(read).toContain('Lcda. Valeria Andrade');
    expect(read).toContain('Consulta');
    expect(read).toContain('Historial clínico #40');
    expect(read).toContain('190.152.10.4');
    expect(denied).toContain('Sin sesión');
    expect(denied).toContain('Acceso denegado');
    expect(denied).toContain('patients');
    expect(denied).toContain('—');
    expect(change).toContain('Usuario #99');
    expect(change).toContain('Cambio de estado');
    expect(change).toContain('Tratamiento #40');
  });

  it('reloads the first page with the chosen filters', async () => {
    searchRequest().flush(page([entry(1)], 45));
    await settle();

    form().patchValue({ userId: 8, action: 'UPDATE', entityType: 'ClinicalRecord' });
    form().patchValue({ from: '2026-10-01', to: '2026-10-30' });
    await settle();

    const requests = backend.match((request) => request.url === '/api/audit-logs');
    const last = requests.at(-1)!;
    expect(last.request.params.get('userId')).toBe('8');
    expect(last.request.params.get('action')).toBe('UPDATE');
    expect(last.request.params.get('entityType')).toBe('ClinicalRecord');
    expect(last.request.params.get('from')).toBe('2026-10-01');
    expect(last.request.params.get('to')).toBe('2026-10-30');
    expect(last.request.params.get('page')).toBe('0');
    requests.forEach((request) => request.flush(page([])));
    await settle();
    expect(element.querySelector('td.empty')?.textContent?.trim()).toBe(
      'No hay registros que coincidan con los filtros.',
    );
  });

  it('does not search with an inverted date range and clears the filters', async () => {
    searchRequest().flush(page([entry(1)]));
    await settle();

    form().patchValue({ from: '2026-10-30', to: '2026-10-01' });
    await settle();
    backend.expectNone((request) => request.url === '/api/audit-logs');
    expect(element.querySelector('.range-error')?.textContent).toContain(
      'La fecha inicial no puede ser posterior a la fecha final.',
    );
    expect(element.querySelector('app-data-table')).toBeNull();

    element.querySelector<HTMLButtonElement>('button.clear')!.click();
    await settle();
    const cleared = searchRequest();
    expect(cleared.request.params.keys().sort()).toEqual(['page', 'size']);
    cleared.flush(page([]));
  });

  it('asks the server for another page', async () => {
    searchRequest().flush(page([entry(1)], 45));
    await settle();

    fixture.componentInstance['goToPage'](2);
    await settle();

    expect(searchRequest().request.params.get('page')).toBe('2');
  });

  it('shows the error and retries', async () => {
    searchRequest().flush(
      { status: 403, detail: 'No tiene permisos para realizar esta acción.' },
      { status: 403, statusText: 'Forbidden' },
    );
    await settle();

    expect(element.textContent).toContain('No se pudo cargar la auditoría');
    expect(element.textContent).toContain('No tiene permisos para realizar esta acción.');
    element.querySelector<HTMLButtonElement>('app-empty-state button')!.click();
    await settle();
    searchRequest().flush(page([entry(1)]));
    await settle();
    expect(rows()).toHaveLength(1);
  });
});
