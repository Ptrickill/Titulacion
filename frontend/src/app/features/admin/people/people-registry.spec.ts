import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { fakePatient } from '../patients/patient-testing';
import { OPEN_REGISTER_STATE } from './people-api';
import { PeopleRegistry } from './people-registry';
import { fakePeoplePage, fakePerson, fakeStaff } from './people-testing';

@Component({ template: '' })
class Blank {}

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

  describe('list', () => {
    beforeEach(async () => {
      TestBed.configureTestingModule({
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      });
      backend = TestBed.inject(HttpTestingController);
      fixture = TestBed.createComponent(PeopleRegistry);
      element = fixture.nativeElement as HTMLElement;
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
      expect(accounts[0]).toBe('Activa');
      expect(accounts[1]).toBe('Inactiva');
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

  it('opens the registration on arrival from "Crear usuario", without a URL parameter', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'admin/users', component: Blank },
          { path: 'admin/people', component: PeopleRegistry },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    const open = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValue({ afterClosed: () => of(undefined) } as never);
    const harness = await RouterTestingHarness.create('/admin/users');

    await TestBed.inject(Router).navigate(['/admin/people'], {
      state: { [OPEN_REGISTER_STATE]: true },
    });
    harness.detectChanges();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/admin/people');
    expect(open).toHaveBeenCalledTimes(1);
    listRequest().flush(fakePeoplePage([]));
    backend.verify();
  });
});
