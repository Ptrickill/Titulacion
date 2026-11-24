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
import { fakePatient } from '../patients/patient-testing';
import { logInForTest } from '../../../core/auth/auth-testing';
import { fakeUser } from './user-testing';
import { PeopleRegistry } from './people-registry';
import { fakePeoplePage, fakePerson, fakeStaff } from './people-testing';

describe('PeopleRegistry (MEJ-02)', () => {
  let fixture: ComponentFixture<PeopleRegistry>;
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

  const people = [
    fakePerson(4, {
      type: 'STAFF',
      firstName: 'Valeria',
      lastName: 'Andrade Mora',
      role: 'PHYSIOTHERAPIST',
      accountStatus: 'ACTIVE',
      userId: 12,
    }),
    fakePerson(5, {
      type: 'STAFF',
      firstName: 'Paula',
      lastName: 'Herrera Vásconez',
      nationalId: null,
      phone: null,
      role: 'OPERATIONS_DIRECTOR',
      accountStatus: 'INACTIVE',
      userId: 13,
      needsCompletion: true,
    }),
    fakePerson(20, {
      firstName: 'Rosa Elena',
      lastName: 'Ortiz Vega',
      accountStatus: 'PENDING_ACTIVATION',
      userId: 31,
      emailChangePending: true,
    }),
    fakePerson(21, { firstName: 'Luis', lastName: 'Quishpe Tipán' }),
  ];

  const STATS = { activeAccounts: 7, physiotherapists: 3, portalPatients: 4, administrators: 1 };
  const rowButton = (row: number, selector: string) =>
    element.querySelectorAll('tbody tr')[row].querySelector<HTMLButtonElement>(selector);

  describe('list', () => {
    beforeEach(async () => {
      TestBed.configureTestingModule({
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      });
      backend = TestBed.inject(HttpTestingController);
      // The logged-in administrator is the account 12 (Valeria Andrade, in these rows)
      logInForTest('ADMINISTRATOR', 'Valeria Andrade Mora', 12);
      fixture = TestBed.createComponent(PeopleRegistry);
      element = fixture.nativeElement as HTMLElement;
      await fixture.whenStable();
      backend.expectOne('/api/people/stats').flush(STATS);
      await fixture.whenStable();
    });

    afterEach(() => backend.verify());

    it('lists staff and patients with their role and the status of their account', async () => {
      const request = listRequest();
      expect(request.request.params.get('q')).toBe('');
      expect(request.request.params.has('type')).toBe(false);
      expect(request.request.params.has('role')).toBe(false);
      request.flush(fakePeoplePage(people));
      await fixture.whenStable();

      expect(element.querySelector('h1')?.textContent).toContain('Registro de Usuarios');
      expect(
        Array.from(element.querySelectorAll('th')).map((th) => th.textContent?.trim()),
      ).toEqual(['Persona y rol', 'Contacto', 'Cuenta', 'Acciones']);
      expect(rowNames()).toEqual([
        'Valeria Andrade Mora',
        'Paula Herrera Vásconez',
        'Rosa Elena Ortiz Vega',
        'Luis Quishpe Tipán',
      ]);
      // The role goes under the name
      const roles = Array.from(element.querySelectorAll('tbody app-status-badge.role')).map((b) =>
        b.textContent?.trim(),
      );
      expect(roles).toEqual(['Fisioterapeuta', 'Dirección operativa', 'Paciente', 'Paciente']);
      expect(cellTexts(2)[0]).toContain('0987654321');
      const accounts = cellTexts(3);
      expect(accounts[0]).toContain('Activa');
      expect(accounts[0]).toContain('Último acceso: Nunca');
      expect(accounts[1]).toContain('Inactiva');
      expect(accounts[2]).toContain('Pendiente de activación');
      expect(accounts[2]).toContain('Correo por confirmar');
      expect(accounts[3]).toBe('Sin cuenta');
      expect(cellTexts(1)[1]).toContain('Datos por completar');
      expect(cellTexts(1)[1]).toContain('Cédula: por completar');
      const links = Array.from(element.querySelectorAll('tbody .name-link')).map((a) =>
        a.getAttribute('href'),
      );
      expect(links).toEqual([
        '/admin/people/staff/4',
        '/admin/people/staff/5',
        '/admin/people/patients/20',
        '/admin/people/patients/21',
      ]);
    });

    it('filters by type and, for the staff, by role; another type clears the role', async () => {
      listRequest().flush(fakePeoplePage(people));
      await fixture.whenStable();
      expect(element.querySelector('#people-role-filter')).toBeNull();

      await choose('people-type-filter', 'Personal');
      let request = listRequest();
      expect(request.request.params.get('type')).toBe('STAFF');
      expect(request.request.params.get('page')).toBe('0');
      request.flush(fakePeoplePage(people.slice(0, 2)));
      await fixture.whenStable();

      await choose('people-role-filter', 'Fisioterapeuta');
      request = listRequest();
      expect(request.request.params.get('type')).toBe('STAFF');
      expect(request.request.params.get('role')).toBe('PHYSIOTHERAPIST');
      request.flush(fakePeoplePage(people.slice(0, 1)));
      await fixture.whenStable();
      expect(rowNames()).toEqual(['Valeria Andrade Mora']);

      await choose('people-type-filter', 'Pacientes');
      request = listRequest();
      expect(request.request.params.get('type')).toBe('PATIENT');
      expect(request.request.params.has('role')).toBe(false);
      request.flush(fakePeoplePage(people.slice(2)));
      await fixture.whenStable();
      expect(element.querySelector('#people-role-filter')).toBeNull();
    });

    it('opens the registration and goes to the record of the new staff member', async () => {
      listRequest().flush(fakePeoplePage([]));
      await fixture.whenStable();
      const staff = fakeStaff(9);
      const open = vi
        .spyOn(TestBed.inject(MatDialog), 'open')
        .mockReturnValue({ afterClosed: () => of({ type: 'STAFF', staff }) } as never);
      const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

      (element.querySelector('button.register') as HTMLButtonElement).click();

      expect(open).toHaveBeenCalled();
      expect(snackBar).toHaveBeenCalledWith(
        `Usuario registrado. Enviamos un enlace de activación a ${staff.email}`,
        'Cerrar',
        { duration: 6000 },
      );
      expect(navigate).toHaveBeenCalledWith(['/admin/people/staff', 9]);
    });

    it('opens the edit form with the full data of a patient', async () => {
      listRequest().flush(fakePeoplePage(people));
      await fixture.whenStable();
      const open = vi
        .spyOn(TestBed.inject(MatDialog), 'open')
        .mockReturnValue({ afterClosed: () => of(undefined) } as never);

      (element.querySelectorAll('button.edit')[2] as HTMLButtonElement).click();
      backend.expectOne('/api/patients/20').flush(fakePatient(20));
      await fixture.whenStable();

      expect(open).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ data: { patient: fakePatient(20) } }),
      );
    });

    it('shows the retry notice when the list cannot be loaded', async () => {
      listRequest().flush(null, { status: 500, statusText: 'Error' });
      await fixture.whenStable();

      expect(element.textContent).toContain('No se pudo cargar el registro');
    });
  });

  describe('accounts (MEJ-02: "Gestión de Usuarios" joined the registry)', () => {
    beforeEach(async () => {
      TestBed.configureTestingModule({
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      });
      backend = TestBed.inject(HttpTestingController);
      logInForTest('ADMINISTRATOR', 'Valeria Andrade Mora', 12);
      fixture = TestBed.createComponent(PeopleRegistry);
      element = fixture.nativeElement as HTMLElement;
      await fixture.whenStable();
      backend.expectOne('/api/people/stats').flush(STATS);
      listRequest().flush(
        fakePeoplePage([
          ...people,
          fakePerson(6, {
            type: 'STAFF',
            firstName: 'Martín',
            lastName: 'Cevallos Ruiz',
            role: 'PHYSIOTHERAPIST',
            accountStatus: 'ACTIVE',
            userId: 14,
            lastLoginAt: new Date().toISOString(),
          }),
        ]),
      );
      await fixture.whenStable();
    });

    afterEach(() => backend.verify());

    it('shows the four indicators of "Gestión de Usuarios" above the list', () => {
      const cards = Array.from(element.querySelectorAll('app-stat-card')).map(
        (card) => card.textContent ?? '',
      );
      expect(cards).toHaveLength(4);
      [
        ['Usuarios activos', '7'],
        ['Fisioterapeutas', '3'],
        ['Pacientes c/ portal', '4'],
        ['Administradores', '1'],
      ].forEach(([label, value], index) => {
        expect(cards[index]).toContain(label);
        expect(cards[index]).toContain(value);
      });
    });
    it('filters by the status of the account', async () => {
      await choose('people-account-filter', 'Sin cuenta');
      const request = listRequest();
      expect(request.request.params.get('accountStatus')).toBe('NONE');
      expect(request.request.params.get('page')).toBe('0');
      request.flush(fakePeoplePage([people[3]]));
      await fixture.whenStable();
      expect(rowNames()).toEqual(['Luis Quishpe Tipán']);

      await choose('people-account-filter', 'Inactiva');
      expect(listRequest().request.params.get('accountStatus')).toBe('INACTIVE');
    });

    it('shows the last access of each account', () => {
      const accounts = cellTexts(3);
      expect(accounts[4]).toMatch(/Último acceso: Hoy, \d{2}:\d{2}/);
      expect(accounts[3]).not.toContain('Último acceso');
    });

    it('offers each account action only where it applies, and never deactivates the own account', () => {
      // Own account (12): deactivation disabled
      // disabledInteractive keeps the tooltip: the button is marked with aria-disabled
      expect(rowButton(0, '.status-action')?.getAttribute('aria-disabled')).toBe('true');
      expect(rowButton(0, '.status-action')?.getAttribute('aria-label')).toBe(
        'No puede desactivar su propia cuenta',
      );
      expect(rowButton(0, '.resend-action')).toBeNull();
      // Inactive account: activate
      expect(rowButton(1, '.status-action')?.getAttribute('aria-label')).toBe(
        'Activar cuenta de Paula Herrera Vásconez',
      );
      // Pending account: resend the invitation
      expect(rowButton(2, '.resend-action')?.getAttribute('aria-label')).toBe(
        'Reenviar invitación a Rosa Elena Ortiz Vega',
      );
      // Without account: only "Editar"
      expect(rowButton(3, '.status-action')).toBeNull();
      expect(rowButton(3, '.resend-action')).toBeNull();
      expect(rowButton(3, 'button.edit')).not.toBeNull();
    });

    it('deactivates after confirming, updates the row and reloads the indicators', async () => {
      const confirm = vi
        .spyOn(TestBed.inject(MatDialog), 'open')
        .mockReturnValue({ afterClosed: () => of(true) } as never);
      const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');

      rowButton(4, '.status-action')!.click();
      await fixture.whenStable();

      expect(confirm.mock.calls[0][1]?.data).toMatchObject({
        title: 'Desactivar cuenta',
        tone: 'danger',
      });
      const request = backend.expectOne('/api/users/14/status');
      expect(request.request.body).toEqual({ active: false });
      request.flush(
        fakeUser(14, 'Martín Cevallos Ruiz', 'PHYSIOTHERAPIST', {
          active: false,
          status: 'INACTIVE',
        }),
      );
      backend.expectOne('/api/people/stats').flush({ ...STATS, activeAccounts: 6 });
      await fixture.whenStable();

      expect(cellTexts(3)[4]).toContain('Inactiva');
      expect(element.querySelector('app-stat-card')?.textContent).toContain('6');
      expect(snackBar).toHaveBeenCalledWith('Cuenta desactivada correctamente.', 'Cerrar', {
        duration: 5000,
      });
    });

    it('resends the invitation of a pending account', async () => {
      const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');

      rowButton(2, '.resend-action')!.click();
      backend
        .expectOne('/api/users/31/resend-invitation')
        .flush(fakeUser(31, 'Rosa Elena Ortiz Vega', 'PATIENT', { status: 'PENDING_ACTIVATION' }));
      backend.expectOne('/api/people/stats').flush(STATS);
      await fixture.whenStable();

      expect(snackBar).toHaveBeenCalledWith(
        'Enviamos un nuevo enlace de activación a juan.perez@correo.ec',
        'Cerrar',
        { duration: 6000 },
      );
    });

    it('changes nothing when the confirmation is cancelled', async () => {
      vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
        afterClosed: () => of(false),
      } as never);

      rowButton(4, '.status-action')!.click();
      await fixture.whenStable();

      backend.expectNone('/api/users/14/status');
    });
  });
});
