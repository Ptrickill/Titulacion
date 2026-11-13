import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestKey } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatMenuHarness } from '@angular/material/menu/testing';
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
  const nameLinks = () => rows().map((row) => row.querySelector<HTMLAnchorElement>('.name a')!);

  /** The menu of each row, in the order of the rows. */
  const menus = () => TestbedHarnessEnvironment.loader(fixture).getAllHarnesses(MatMenuHarness);

  /** Opens the menu and returns the text and the destination of each of its actions. */
  async function actionsOf(menu: MatMenuHarness): Promise<[string, string | null][]> {
    await menu.open();
    const actions: [string, string | null][] = [];
    for (const item of await menu.getItems()) {
      actions.push([await item.getText(), await (await item.host()).getAttribute('href')]);
    }
    await menu.close();
    return actions;
  }

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

  it('offers every action of a row with a treatment in its menu, whatever the status', async () => {
    listRequest().flush(
      page([
        patient(1, 'Juan', 'Pérez', 40, ACTIVE_TREATMENT),
        patient(2, 'María', 'Velasco', 41, { ...ACTIVE_TREATMENT, id: 10, status: 'COMPLETED' }),
        patient(3, 'Pedro', 'Cordero', 42, { ...ACTIVE_TREATMENT, id: 11, status: 'ABANDONED' }),
      ]),
    );
    await fixture.whenStable();

    const [active, completed, abandoned] = await menus();
    expect(await actionsOf(active)).toEqual([
      ['Ver historial', '/physio/patients/1/clinical-record'],
      ['Plan y asignación', '/physio/patients/1/treatments/9'],
      ['Línea de tiempo', '/physio/patients/1/treatments/9/timeline'],
    ]);
    expect(await actionsOf(completed)).toEqual([
      ['Ver historial', '/physio/patients/2/clinical-record'],
      ['Plan y asignación', '/physio/patients/2/treatments/10'],
      ['Línea de tiempo', '/physio/patients/2/treatments/10/timeline'],
      ['Nuevo tratamiento', '/physio/patients/2/clinical-record/41/treatments/new'],
    ]);
    expect(await actionsOf(abandoned)).toEqual([
      ['Ver historial', '/physio/patients/3/clinical-record'],
      ['Plan y asignación', '/physio/patients/3/treatments/11'],
      ['Línea de tiempo', '/physio/patients/3/treatments/11/timeline'],
      ['Nuevo tratamiento', '/physio/patients/3/clinical-record/42/treatments/new'],
    ]);
  });

  it('offers a treatment to a record without one, and a record to a patient without one', async () => {
    listRequest().flush(page([patient(1, 'Lucía', 'Mora', 42), patient(2, 'Andrés', 'Cárdenas')]));
    await fixture.whenStable();

    const [withRecord, withoutRecord] = await menus();
    expect(await actionsOf(withRecord)).toEqual([
      ['Ver historial', '/physio/patients/1/clinical-record'],
      ['Nuevo tratamiento', '/physio/patients/1/clinical-record/42/treatments/new'],
    ]);
    expect(await actionsOf(withoutRecord)).toEqual([
      ['Crear historial', '/physio/patients/2/clinical-record/new'],
    ]);
  });

  it('keeps the actions of each row in one menu button, named after the patient', async () => {
    listRequest().flush(
      page([patient(1, 'Juan', 'Pérez', 40, ACTIVE_TREATMENT), patient(2, 'Andrés', 'Cárdenas')]),
    );
    await fixture.whenStable();

    const triggers = rows().map((row) => Array.from(row.querySelectorAll('button')));
    expect(triggers.map((buttons) => buttons.length)).toEqual([1, 1]);
    expect(triggers.map(([button]) => button.getAttribute('aria-label'))).toEqual([
      'Acciones de Juan Pérez',
      'Acciones de Andrés Cárdenas',
    ]);
    expect(triggers[0][0].getAttribute('aria-haspopup')).toBe('menu');
    // Closed, the only link of a row is the name
    expect(rows().map((row) => row.querySelectorAll('a').length)).toEqual([1, 1]);
  });

  it('moves through the menu with the keyboard and gives the focus back when it closes', async () => {
    listRequest().flush(page([patient(1, 'Juan', 'Pérez', 40, ACTIVE_TREATMENT)]));
    await fixture.whenStable();

    const [menu] = await menus();
    const trigger = rows()[0].querySelector('button')!;
    await menu.open();
    const focused = () => document.activeElement?.textContent?.trim();
    const panel = document.querySelector<HTMLElement>('.mat-mdc-menu-panel')!;
    expect(panel.getAttribute('role')).toBe('menu');
    expect(panel.getAttribute('aria-label')).toBe('Acciones de Juan Pérez');

    const [first, second] = await menu.getItems();
    await first.focus();
    expect(focused()).toBe('Ver historial');
    await (await first.host()).sendKeys(TestKey.DOWN_ARROW);
    expect(focused()).toBe('Plan y asignación');
    await (await second.host()).sendKeys(TestKey.DOWN_ARROW);
    expect(focused()).toBe('Línea de tiempo');

    await (await second.host()).sendKeys(TestKey.ESCAPE);
    await fixture.whenStable();
    expect(await menu.isOpen()).toBe(false);
    expect(document.activeElement).toBe(trigger);
  });

  it('links the name of the patient to the record, or to its creation without one', async () => {
    listRequest().flush(
      page([patient(1, 'Juan', 'Pérez', 40, ACTIVE_TREATMENT), patient(2, 'Andrés', 'Cárdenas')]),
    );
    await fixture.whenStable();

    const [withRecord, withoutRecord] = nameLinks();
    expect(withRecord.textContent?.trim()).toBe('Juan Pérez');
    expect(withRecord.getAttribute('href')).toBe('/physio/patients/1/clinical-record');
    expect(withRecord.getAttribute('aria-label')).toBe('Ver historial de Juan Pérez');
    expect(withoutRecord.textContent?.trim()).toBe('Andrés Cárdenas');
    expect(withoutRecord.getAttribute('href')).toBe('/physio/patients/2/clinical-record/new');
    expect(withoutRecord.getAttribute('aria-label')).toBe('Crear historial de Andrés Cárdenas');
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
