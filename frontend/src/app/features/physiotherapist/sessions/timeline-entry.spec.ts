import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Page } from '../../../core/http/page';
import { CurrentTreatment, PhysioPatient } from '../clinical-records/clinical-record-api';
import { TimelineEntry } from './timeline-entry';

@Component({ template: 'Línea de tiempo del tratamiento' })
class TimelineStub {}

const ACTIVE: CurrentTreatment = {
  id: 9,
  diagnosisCode: 'M54.5',
  diagnosisDescription: 'Lumbago no especificado',
  severity: 'MODERATE',
  plannedSessions: 12,
  status: 'ACTIVE',
};

function patient(
  id: number,
  firstName: string,
  lastName: string,
  currentTreatment: CurrentTreatment | null = null,
  clinicalRecordId: number | null = currentTreatment ? id + 100 : null,
): PhysioPatient {
  return {
    id,
    nationalId: '1710034065',
    medicalRecordNumber: '1710034065',
    firstName,
    lastName,
    age: 45,
    clinicalRecordId,
    currentTreatment,
  };
}

function page(content: PhysioPatient[], totalElements = content.length): Page<PhysioPatient> {
  return { content, page: 0, size: 10, totalElements, totalPages: Math.ceil(totalElements / 10) };
}

describe('TimelineEntry', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let element: HTMLElement;

  const listRequest = (): TestRequest =>
    backend.expectOne((request) => request.url === '/api/physio/patients');
  const rows = () => Array.from(element.querySelectorAll<HTMLElement>('tbody tr'));
  const rowTexts = () => rows().map((row) => row.textContent!.replace(/\s+/g, ' ').trim());
  const emptyState = () => element.querySelector('app-empty-state .title')?.textContent?.trim();

  async function settle(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  /** Opens /physio/timeline and answers the first page of patients. */
  async function open(first: Page<PhysioPatient>): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/physio/timeline', TimelineEntry);
    element = harness.routeNativeElement as HTMLElement;
    const call = listRequest();
    expect(call.request.params.get('q')).toBe('');
    expect(call.request.params.get('page')).toBe('0');
    expect(call.request.params.get('size')).toBe('10');
    call.flush(first);
    await settle();
  }

  async function search(text: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement>('input[type="search"]')!;
    input.value = text;
    input.dispatchEvent(new Event('input'));
    // The search waits 300 ms after the last key
    await new Promise((resolve) => setTimeout(resolve, 350));
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'physio/timeline', component: TimelineEntry },
          {
            path: 'physio/patients/:patientId/treatments/:treatmentId/timeline',
            component: TimelineStub,
          },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists the patients with the diagnosis and the planned sessions of their current treatment', async () => {
    await open(
      page([
        patient(1, 'Juan Carlos', 'Pérez Gómez', ACTIVE),
        patient(2, 'María Belén', 'Velasco', {
          ...ACTIVE,
          id: 10,
          diagnosisCode: 'M75.1',
          diagnosisDescription: 'Síndrome del manguito rotador',
          plannedSessions: 20,
          status: 'COMPLETED',
        }),
      ]),
    );

    expect(element.querySelector('h1')?.textContent).toContain('Línea de Tiempo Terapéutica');
    const headers = Array.from(element.querySelectorAll('th')).map((th) => th.textContent?.trim());
    expect(headers).toEqual(['Paciente', 'Diagnóstico', 'Tratamiento', 'Acción']);

    const [active, completed] = rowTexts();
    expect(active).toContain('Juan Carlos Pérez Gómez');
    expect(active).toContain('Cédula: 1710034065 • 45 años');
    expect(active).toContain('Lumbago no especificado');
    expect(active).toContain('M54.5');
    expect(active).toContain('Activo');
    expect(rows()[0].querySelector('.planned')?.textContent?.trim()).toBe(
      '12 sesiones planificadas',
    );
    expect(completed).toContain('Síndrome del manguito rotador');
    expect(completed).toContain('Finalizado');
    expect(rows()[1].querySelector('.planned')?.textContent?.trim()).toBe(
      '20 sesiones planificadas',
    );
    expect(element.querySelector('.footer')?.textContent?.replace(/\s+/g, ' ')).toContain(
      'Mostrando 1–2 de 2 pacientes',
    );
  });

  it('labels a patient without treatment and gives it no timeline', async () => {
    await open(
      page([
        patient(1, 'Juan', 'Pérez', ACTIVE),
        // With a clinical record but no treatment, and without a clinical record
        patient(2, 'Lucía', 'Mora', null, 42),
        patient(3, 'Andrés', 'Cárdenas'),
      ]),
    );

    const [withTreatment, withRecord, withoutRecord] = rows();
    expect(withTreatment.textContent).not.toContain('Sin tratamiento');
    expect(withTreatment.querySelectorAll('a').length).toBe(1);
    for (const row of [withRecord, withoutRecord]) {
      expect(row.querySelector('app-status-badge')?.textContent?.trim()).toBe('Sin tratamiento');
      expect(row.querySelector('.planned')).toBeNull();
      expect(row.querySelector('a')).toBeNull();
    }
  });

  it('opens the timeline of the current treatment of the patient chosen', async () => {
    await open(
      page([
        patient(1, 'Juan', 'Pérez', ACTIVE),
        patient(2, 'María', 'Velasco', { ...ACTIVE, id: 10, status: 'ABANDONED' }),
      ]),
    );

    const links = rows().map((row) => row.querySelector('a')!);
    expect(links.map((a) => a.getAttribute('href'))).toEqual([
      '/physio/patients/1/treatments/9/timeline',
      '/physio/patients/2/treatments/10/timeline',
    ]);
    expect(links[1].textContent?.trim()).toBe('Abrir línea de tiempo');
    expect(links[1].getAttribute('aria-label')).toBe('Abrir la línea de tiempo de María Velasco');

    links[1].click();
    await settle();

    expect(TestBed.inject(Router).url).toBe('/physio/patients/2/treatments/10/timeline');
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain(
      'Línea de tiempo del tratamiento',
    );
  });

  it('searches by name or national ID as the user types, from the first page', async () => {
    await open(page([patient(1, 'Ana', 'Benítez', ACTIVE)], 25));

    await search(' perez ');
    const byName = listRequest();
    expect(byName.request.params.get('q')).toBe('perez');
    expect(byName.request.params.get('page')).toBe('0');
    byName.flush(page([patient(4, 'Juan', 'Pérez', ACTIVE)]));
    await settle();
    expect(rowTexts()).toHaveLength(1);
    expect(rowTexts()[0]).toContain('Juan Pérez');

    await search('1710');
    const byNationalId = listRequest();
    expect(byNationalId.request.params.get('q')).toBe('1710');
    byNationalId.flush(page([]));
    await settle();

    // A search without matches is not the empty state: the patients exist
    expect(element.querySelector('td.empty')?.textContent?.trim()).toBe(
      'Ningún paciente coincide con la búsqueda.',
    );
    expect(emptyState()).toBeUndefined();
  });

  it('keeps the search when it changes the page', async () => {
    await open(page([patient(1, 'Ana', 'Benítez', ACTIVE)], 25));
    await search('be');
    listRequest().flush(page([patient(1, 'Ana', 'Benítez', ACTIVE)], 14));
    await settle();

    element.querySelector<HTMLButtonElement>('button[aria-label="Página siguiente"]')!.click();
    await settle();

    const next = listRequest();
    expect(next.request.params.get('q')).toBe('be');
    expect(next.request.params.get('page')).toBe('1');
    next.flush({ ...page([patient(11, 'Belén', 'Vera')], 14), page: 1 });
    await settle();
    expect(rowTexts()[0]).toContain('Belén Vera');
  });

  it('shows the empty state when there are no patients', async () => {
    await open(page([]));

    expect(emptyState()).toBe('No hay pacientes registrados');
    expect(element.querySelector('table')).toBeNull();
    expect(element.querySelector('input[type="search"]')).not.toBeNull();
  });

  it('lets the user retry when the patients cannot be loaded', async () => {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/physio/timeline', TimelineEntry);
    element = harness.routeNativeElement as HTMLElement;
    listRequest().flush(null, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(emptyState()).toBe('No se pudieron cargar los pacientes');
    Array.from(element.querySelectorAll('button'))
      .find((button) => button.textContent?.trim() === 'Reintentar')!
      .click();
    await settle();

    listRequest().flush(page([patient(1, 'Juan', 'Pérez', ACTIVE)]));
    await settle();
    expect(rowTexts()[0]).toContain('Juan Pérez');
  });
});
