import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { logInForTest } from '../../../core/auth/auth-testing';
import { fakePatient } from '../../../shared/people/patient-testing';
import { fakePeoplePage, fakePerson } from '../../../shared/people/people-testing';
import { fakeUser } from '../../../shared/people/user-testing';
import { PatientRegistry } from './patient-registry';

describe('PatientRegistry (MEJ-05: the reception registers patients only)', () => {
  let fixture: ComponentFixture<PatientRegistry>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const listRequest = (): TestRequest =>
    backend.expectOne((request) => request.url === '/api/people');
  const rowNames = () =>
    Array.from(element.querySelectorAll('tbody .name-link')).map((cell) =>
      cell.textContent?.trim(),
    );
  const cellTexts = (column: number) =>
    Array.from(element.querySelectorAll(`tbody tr td:nth-child(${column})`)).map((cell) =>
      cell.textContent?.replace(/\s+/g, ' ').trim(),
    );
  const rowButton = (row: number, selector: string) =>
    element.querySelectorAll('tbody tr')[row].querySelector<HTMLButtonElement>(selector);

  async function choose(selectId: string, optionText: string): Promise<void> {
    const select = element.querySelector<HTMLElement>(`#${selectId}`)!;
    (select.querySelector('.mat-mdc-select-trigger') as HTMLElement).click();
    await fixture.whenStable();
    const option = Array.from(
      document.getElementById(`${selectId}-panel`)!.querySelectorAll('mat-option'),
    ).find((o) => o.textContent?.trim() === optionText) as HTMLElement;
    option.click();
    await fixture.whenStable();
  }

  const patients = [
    fakePerson(20, {
      firstName: 'Rosa Elena',
      lastName: 'Ortiz Vega',
      accountStatus: 'PENDING_ACTIVATION',
      userId: 31,
      emailChangePending: true,
    }),
    fakePerson(21, { firstName: 'Luis', lastName: 'Quishpe Tipán', needsCompletion: true }),
    fakePerson(22, {
      firstName: 'Daniela Sofía',
      lastName: 'Zambrano Loor',
      accountStatus: 'ACTIVE',
      userId: 33,
      lastLoginAt: new Date().toISOString(),
    }),
  ];
  const STATS = { patients: 15, portalActive: 3, portalPending: 1, withoutPortal: 11 };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    logInForTest('ADMINISTRATOR', 'Gabriela Salazar Ponce', 5);
    fixture = TestBed.createComponent(PatientRegistry);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    backend.expectOne('/api/people/stats').flush(STATS);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('lists the patients with the status of their portal access, without type or role filters', async () => {
    const request = listRequest();
    expect(request.request.params.get('q')).toBe('');
    expect(request.request.params.has('type')).toBe(false);
    expect(request.request.params.has('role')).toBe(false);
    request.flush(fakePeoplePage(patients));
    await fixture.whenStable();

    expect(element.querySelector('h1')?.textContent).toContain('Registro de Pacientes');
    expect(element.querySelector('#people-type-filter')).toBeNull();
    expect(element.querySelector('#people-role-filter')).toBeNull();
    expect(Array.from(element.querySelectorAll('th')).map((th) => th.textContent?.trim())).toEqual([
      'Persona y rol',
      'Contacto',
      'Cuenta',
      'Acciones',
    ]);
    expect(rowNames()).toEqual([
      'Rosa Elena Ortiz Vega',
      'Luis Quishpe Tipán',
      'Daniela Sofía Zambrano Loor',
    ]);
    const accounts = cellTexts(3);
    expect(accounts[0]).toContain('Pendiente de activación');
    expect(accounts[0]).toContain('Correo por confirmar');
    expect(accounts[1]).toBe('Sin cuenta');
    expect(accounts[2]).toMatch(/Último acceso: Hoy, \d{2}:\d{2}/);
    expect(cellTexts(1)[1]).toContain('Datos por completar');
    // Each name opens the patient's record of the reception
    expect(
      Array.from(element.querySelectorAll('tbody .name-link')).map((a) => a.getAttribute('href')),
    ).toEqual(['/admin/patients/20', '/admin/patients/21', '/admin/patients/22']);
  });

  it('marks a patient blocked by an erasure instead of "Datos por completar" (MEJ-06)', async () => {
    listRequest().flush(
      fakePeoplePage([
        fakePerson(41, {
          firstName: 'Elena',
          lastName: 'Proaño',
          email: null,
          phone: null,
          needsCompletion: false,
          blocked: true,
        }),
      ]),
    );
    await fixture.whenStable();

    expect(cellTexts(1)[0]).toContain('Datos bloqueados');
    expect(cellTexts(1)[0]).not.toContain('Datos por completar');
    expect(cellTexts(2)[0]).toContain('—');
  });

  it('shows the four indicators of the patients and their portal access', () => {
    listRequest().flush(fakePeoplePage([]));
    const cards = Array.from(element.querySelectorAll('app-stat-card')).map(
      (card) => card.textContent ?? '',
    );
    [
      ['Pacientes registrados', '15'],
      ['Portal activo', '3'],
      ['Portal pendiente', '1'],
      ['Sin acceso al portal', '11'],
    ].forEach(([label, value], index) => {
      expect(cards[index]).toContain(label);
      expect(cards[index]).toContain(value);
    });
  });

  it('filters by the status of the portal account from the first page', async () => {
    listRequest().flush(fakePeoplePage(patients));
    await fixture.whenStable();

    await choose('patients-account-filter', 'Sin cuenta');
    const request = listRequest();
    expect(request.request.params.get('accountStatus')).toBe('NONE');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(fakePeoplePage([patients[1]]));
    await fixture.whenStable();
    expect(rowNames()).toEqual(['Luis Quishpe Tipán']);
  });

  it('registers a patient directly, without asking the type, and opens the record', async () => {
    listRequest().flush(fakePeoplePage([]));
    await fixture.whenStable();
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of({ type: 'PATIENT', patient: fakePatient(40) }),
    } as never);
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const register = element.querySelector('button.register') as HTMLButtonElement;
    expect(register.textContent).toContain('Registrar paciente');
    register.click();

    expect(open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { type: 'PATIENT' } }),
    );
    expect(snackBar).toHaveBeenCalledWith('Paciente registrado correctamente.', 'Cerrar', {
      duration: 5000,
    });
    expect(navigate).toHaveBeenCalledWith(['/admin/patients', 40]);
  });

  it('opens the edit form with the full data of the patient', async () => {
    listRequest().flush(fakePeoplePage(patients));
    await fixture.whenStable();
    const open = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValue({ afterClosed: () => of(undefined) } as never);

    rowButton(1, 'button.edit')!.click();
    backend.expectOne('/api/patients/21').flush(fakePatient(21));
    await fixture.whenStable();

    expect(open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { patient: fakePatient(21) } }),
    );
  });

  it('resends the invitation of a pending portal account and reloads the indicators', async () => {
    listRequest().flush(fakePeoplePage(patients));
    await fixture.whenStable();
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');

    expect(rowButton(0, '.resend-action')?.getAttribute('aria-label')).toBe(
      'Reenviar invitación a Rosa Elena Ortiz Vega',
    );
    rowButton(0, '.resend-action')!.click();
    backend
      .expectOne('/api/users/31/resend-invitation')
      .flush(fakeUser(31, 'Rosa Elena Ortiz Vega', 'PATIENT', { status: 'PENDING_ACTIVATION' }));
    backend.expectOne('/api/people/stats').flush(STATS);
    await fixture.whenStable();

    expect(snackBar).toHaveBeenCalled();
  });

  it('deactivates a portal account after confirming and updates the row', async () => {
    listRequest().flush(fakePeoplePage(patients));
    await fixture.whenStable();
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(true),
    } as never);

    rowButton(2, '.status-action')!.click();
    await fixture.whenStable();
    const request = backend.expectOne('/api/users/33/status');
    expect(request.request.body).toEqual({ active: false });
    request.flush(
      fakeUser(33, 'Daniela Sofía Zambrano Loor', 'PATIENT', { active: false, status: 'INACTIVE' }),
    );
    backend.expectOne('/api/people/stats').flush({ ...STATS, portalActive: 2 });
    await fixture.whenStable();

    expect(cellTexts(3)[2]).toContain('Inactiva');
    expect(element.querySelectorAll('app-stat-card')[1].textContent).toContain('2');
  });

  it('shows the retry notice when the list cannot be loaded', async () => {
    listRequest().flush(null, { status: 500, statusText: 'Error' });
    await fixture.whenStable();

    expect(element.textContent).toContain('No se pudo cargar el registro');
  });
});
