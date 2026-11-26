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
import { fakePeoplePage, fakePerson, fakeStaff } from '../../../shared/people/people-testing';
import { fakeUser } from '../../../shared/people/user-testing';
import { StaffList } from './staff-list';

describe('StaffList (MEJ-05: "Gestión de Personal" of the operations director)', () => {
  let fixture: ComponentFixture<StaffList>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const listRequest = (): TestRequest =>
    backend.expectOne((request) => request.url === '/api/staff');
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

  const staff = [
    fakePerson(4, {
      type: 'STAFF',
      firstName: 'Paula',
      lastName: 'Herrera Vásconez',
      role: 'OPERATIONS_DIRECTOR',
      accountStatus: 'ACTIVE',
      userId: 12,
    }),
    fakePerson(5, {
      type: 'STAFF',
      firstName: 'Gabriela',
      lastName: 'Salazar Ponce',
      role: 'ADMINISTRATOR',
      accountStatus: 'PENDING_ACTIVATION',
      userId: 13,
    }),
    fakePerson(6, {
      type: 'STAFF',
      firstName: 'Martín',
      lastName: 'Cevallos Ruiz',
      nationalId: null,
      role: 'PHYSIOTHERAPIST',
      accountStatus: 'INACTIVE',
      userId: 14,
      needsCompletion: true,
    }),
  ];
  const STATS = { activeAccounts: 6, physiotherapists: 3, receptionists: 2, directors: 1 };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    // The logged-in director is the account 12 (Paula Herrera, in these rows)
    logInForTest('OPERATIONS_DIRECTOR', 'Paula Herrera Vásconez', 12);
    fixture = TestBed.createComponent(StaffList);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    backend.expectOne('/api/staff/stats').flush(STATS);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('lists the staff with its role and the status of each account', async () => {
    listRequest().flush(fakePeoplePage(staff));
    await fixture.whenStable();

    expect(element.querySelector('h1')?.textContent).toContain('Gestión de Personal');
    expect(rowNames()).toEqual([
      'Paula Herrera Vásconez',
      'Gabriela Salazar Ponce',
      'Martín Cevallos Ruiz',
    ]);
    const roles = Array.from(element.querySelectorAll('tbody app-status-badge.role')).map((b) =>
      b.textContent?.trim(),
    );
    expect(roles).toEqual(['Dirección operativa', 'Recepción', 'Fisioterapeuta']);
    const accounts = cellTexts(3);
    expect(accounts[0]).toContain('Activa');
    expect(accounts[1]).toContain('Pendiente de activación');
    expect(accounts[2]).toContain('Inactiva');
    expect(cellTexts(1)[2]).toContain('Cédula: por completar');
    expect(
      Array.from(element.querySelectorAll('tbody .name-link')).map((a) => a.getAttribute('href')),
    ).toEqual(['/director/staff/4', '/director/staff/5', '/director/staff/6']);
  });

  it('shows the four indicators of the staff', () => {
    listRequest().flush(fakePeoplePage([]));
    const cards = Array.from(element.querySelectorAll('app-stat-card')).map(
      (card) => card.textContent ?? '',
    );
    [
      ['Cuentas activas', '6'],
      ['Fisioterapeutas', '3'],
      ['Recepción', '2'],
      ['Dirección operativa', '1'],
    ].forEach(([label, value], index) => {
      expect(cards[index]).toContain(label);
      expect(cards[index]).toContain(value);
    });
  });

  it('filters by role and by the status of the account, which has no "Sin cuenta"', async () => {
    listRequest().flush(fakePeoplePage(staff));
    await fixture.whenStable();

    await choose('staff-role-filter', 'Recepción');
    let request = listRequest();
    expect(request.request.params.get('role')).toBe('ADMINISTRATOR');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(fakePeoplePage([staff[1]]));
    await fixture.whenStable();

    const select = element.querySelector<HTMLElement>('#staff-account-filter')!;
    (select.querySelector('.mat-mdc-select-trigger') as HTMLElement).click();
    await fixture.whenStable();
    const options = Array.from(
      document.getElementById('staff-account-filter-panel')!.querySelectorAll('mat-option'),
    ).map((option) => option.textContent?.trim());
    expect(options).toEqual(['Todos', 'Pendiente de activación', 'Activa', 'Inactiva']);
    (document.querySelectorAll('#staff-account-filter-panel mat-option')[3] as HTMLElement).click();
    await fixture.whenStable();
    request = listRequest();
    expect(request.request.params.get('role')).toBe('ADMINISTRATOR');
    expect(request.request.params.get('accountStatus')).toBe('INACTIVE');
  });

  it('registers a staff member on the role step and opens its record', async () => {
    listRequest().flush(fakePeoplePage([]));
    await fixture.whenStable();
    const created = fakeStaff(9);
    const open = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValue({ afterClosed: () => of({ type: 'STAFF', staff: created }) } as never);
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (element.querySelector('button.register') as HTMLButtonElement).click();

    expect(open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { type: 'STAFF' } }),
    );
    expect(snackBar).toHaveBeenCalledWith(
      `Personal registrado. Enviamos un enlace de activación a ${created.email}`,
      'Cerrar',
      { duration: 6000 },
    );
    expect(navigate).toHaveBeenCalledWith(['/director/staff', 9]);
  });

  it('opens the edit form with the full data of the staff member', async () => {
    listRequest().flush(fakePeoplePage(staff));
    await fixture.whenStable();
    const open = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValue({ afterClosed: () => of(undefined) } as never);

    rowButton(1, 'button.edit')!.click();
    backend.expectOne('/api/staff/5').flush(fakeStaff(5));
    await fixture.whenStable();

    expect(open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { staff: fakeStaff(5) } }),
    );
  });

  it('never deactivates the own account and reactivates another one', async () => {
    listRequest().flush(fakePeoplePage(staff));
    await fixture.whenStable();
    expect(rowButton(0, '.status-action')?.getAttribute('aria-disabled')).toBe('true');
    expect(rowButton(0, '.status-action')?.getAttribute('aria-label')).toBe(
      'No puede desactivar su propia cuenta',
    );
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(true),
    } as never);

    rowButton(2, '.status-action')!.click();
    await fixture.whenStable();
    const request = backend.expectOne('/api/users/14/status');
    expect(request.request.body).toEqual({ active: true });
    request.flush(fakeUser(14, 'Martín Cevallos Ruiz', 'PHYSIOTHERAPIST', { status: 'ACTIVE' }));
    backend.expectOne('/api/staff/stats').flush({ ...STATS, activeAccounts: 7 });
    await fixture.whenStable();

    expect(cellTexts(3)[2]).toContain('Activa');
    expect(element.querySelector('app-stat-card')?.textContent).toContain('7');
  });
});
