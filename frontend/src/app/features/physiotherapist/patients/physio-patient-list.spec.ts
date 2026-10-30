import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Page } from '../../../core/http/page';
import { CurrentTreatment, PhysioPatient } from '../clinical-records/clinical-record-api';
import { PhysioPatientList } from './physio-patient-list';

function patient(
  id: number,
  firstName: string,
  lastName: string,
  clinicalRecordId: number | null = null,
  currentTreatment: CurrentTreatment | null = null,
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

const ACTIVE_TREATMENT: CurrentTreatment = {
  id: 9,
  diagnosisCode: 'M51.1',
  diagnosisDescription: 'Trastorno de disco lumbar y otros, con radiculopatía',
  severity: 'MODERATE',
  plannedSessions: 12,
  status: 'ACTIVE',
};

describe('PhysioPatientList', () => {
  let fixture: ComponentFixture<PhysioPatientList>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const listRequest = (): TestRequest =>
    backend.expectOne((request) => request.url === '/api/physio/patients');
  const rows = () => Array.from(element.querySelectorAll('tbody tr'));

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PhysioPatientList);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('shows each patient with the summary of the current treatment', async () => {
    listRequest().flush(
      page([
        patient(1, 'Juan Carlos', 'Pérez Gómez', 40, ACTIVE_TREATMENT),
        patient(2, 'María Belén', 'Velasco', 41, {
          ...ACTIVE_TREATMENT,
          id: 10,
          severity: 'SEVERE',
          status: 'COMPLETED',
        }),
        patient(3, 'Andrés', 'Cárdenas'),
      ]),
    );
    await fixture.whenStable();

    expect(element.querySelector('h1')?.textContent).toContain('Gestión de Pacientes');
    const headers = Array.from(element.querySelectorAll('th')).map((th) => th.textContent?.trim());
    expect(headers).toEqual([
      'Paciente',
      'Diagnóstico principal',
      'Sesiones',
      'Estado',
      'Acciones',
    ]);

    const [active, completed, none] = rows().map((row) => row.textContent!.replace(/\s+/g, ' '));
    expect(active).toContain('Juan Carlos Pérez Gómez');
    expect(active).toContain('Cédula: 1710034065 • 45 años');
    expect(active).toContain('Trastorno de disco lumbar y otros, con radiculopatía');
    expect(active).toContain('Severidad: Moderada');
    const sessions = rows()[0].querySelector('.sessions')!;
    expect(sessions.textContent?.trim()).toBe('12');
    expect(sessions.getAttribute('aria-label')).toBe('12 sesiones planificadas');
    expect(active).toContain('Activo');
    expect(completed).toContain('Grave');
    expect(completed).toContain('Finalizado');
    expect(none).toContain('Sin valoración inicial');
    expect(none).toContain('Sin historial');
  });

  it('links to the record view and the plan, and offers a record or treatment when it applies', async () => {
    listRequest().flush(
      page([
        patient(1, 'Juan', 'Pérez', 40, ACTIVE_TREATMENT),
        patient(2, 'María', 'Velasco', 41, { ...ACTIVE_TREATMENT, status: 'ABANDONED' }),
        patient(3, 'Andrés', 'Cárdenas'),
      ]),
    );
    await fixture.whenStable();

    const links = rows().map((row) =>
      Array.from(row.querySelectorAll('a')).map((a) => [
        a.textContent?.trim(),
        a.getAttribute('href'),
      ]),
    );
    expect(links[0]).toEqual([
      ['Ver historial', '/physio/patients/1/clinical-record'],
      ['Plan y asignación', '/physio/patients/1/treatments/9'],
    ]);
    expect(links[1]).toEqual([
      ['Ver historial', '/physio/patients/2/clinical-record'],
      ['Plan y asignación', '/physio/patients/2/treatments/9'],
      ['Nuevo tratamiento', '/physio/patients/2/clinical-record/41/treatments/new'],
    ]);
    expect(links[2]).toEqual([['Crear historial', '/physio/patients/3/clinical-record/new']]);
  });

  it('searches as the user types, from the first page', async () => {
    listRequest().flush(page([patient(1, 'Ana', 'Benítez')], 12));
    await fixture.whenStable();

    const input = element.querySelector<HTMLInputElement>('input[type="search"]')!;
    input.value = ' perez ';
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 350));
    await fixture.whenStable();

    const search = listRequest();
    expect(search.request.params.get('q')).toBe('perez');
    expect(search.request.params.get('page')).toBe('0');
    search.flush(page([]));
    await fixture.whenStable();
    expect(element.querySelector('td.empty')?.textContent?.trim()).toBe(
      'Ningún paciente coincide con la búsqueda.',
    );
  });
});
