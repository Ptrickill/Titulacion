import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import type { MockInstance } from 'vitest';
import { logInForTest } from '../../../core/auth/auth-testing';
import { UserList } from './user-list';
import { fakeUser } from './user-testing';

describe('UserList', () => {
  let fixture: ComponentFixture<UserList>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const users = [
    fakeUser(1, 'Ricardo Salinas', 'PHYSIOTHERAPIST', {
      lastLoginAt: new Date().toISOString(),
    }),
    fakeUser(2, 'José Montaño', 'PHYSIOTHERAPIST'),
    fakeUser(3, 'Administrador Rehab', 'ADMINISTRATOR'),
    fakeUser(4, 'Director General', 'OPERATIONS_DIRECTOR', { active: false }),
    fakeUser(5, 'Rosa Ortiz', 'PATIENT'),
  ];

  const rowNames = () =>
    Array.from(element.querySelectorAll('tbody .user-name')).map((cell) =>
      cell.textContent?.trim(),
    );
  const statValues = () =>
    Array.from(element.querySelectorAll('app-stat-card')).map((card) => card.textContent);

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(UserList);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    backend.expectOne('/api/users').flush(users);
    await fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('shows the indicators and every account with role, last access and status', async () => {
    await render();

    expect(element.querySelector('h1')?.textContent).toContain('Gestión de Usuarios y Roles');
    const stats = statValues();
    expect(stats[0]).toContain('Usuarios activos');
    expect(stats[0]).toContain('4');
    expect(stats[1]).toContain('2');
    expect(stats[2]).toContain('1');
    expect(stats[3]).toContain('1');
    expect(rowNames()).toEqual([
      'Ricardo Salinas',
      'José Montaño',
      'Administrador Rehab',
      'Director General',
      'Rosa Ortiz',
    ]);
    const firstRow = element.querySelector('tbody tr')!.textContent!;
    expect(firstRow).toContain('usuario1@rehabelite.test');
    expect(firstRow).toContain('Fisioterapeuta');
    expect(firstRow).toMatch(/Hoy, \d{2}:\d{2}/);
    expect(firstRow).toContain('Activo');
    expect(element.querySelectorAll('tbody tr')[3].textContent).toContain('Inactivo');
    expect(element.querySelectorAll('tbody tr')[1].textContent).toContain('Nunca');
  });

  it('filters by role and searches by name or email ignoring accents', async () => {
    await render();

    const patientsFilter = Array.from(element.querySelectorAll('mat-button-toggle button')).find(
      (button) => button.textContent?.includes('Paciente (Portal)'),
    ) as HTMLButtonElement;
    patientsFilter.click();
    await fixture.whenStable();
    expect(rowNames()).toEqual(['Rosa Ortiz']);

    (element.querySelector('mat-button-toggle button') as HTMLButtonElement).click();
    const search = element.querySelector<HTMLInputElement>('input[type=search]')!;
    search.value = 'montano';
    search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(rowNames()).toEqual(['José Montaño']);

    search.value = 'usuario3@';
    search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(rowNames()).toEqual(['Administrador Rehab']);
  });

  it('reloads the list and confirms after creating a user', async () => {
    const created = fakeUser(6, 'Lucía Mora', 'PHYSIOTHERAPIST');
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(created),
    } as ReturnType<MatDialog['open']>);
    const notice = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    await render();

    (element.querySelector('app-page-header button') as HTMLButtonElement).click();
    backend.expectOne('/api/users').flush([...users, created]);
    await fixture.whenStable();

    expect(notice).toHaveBeenCalledWith('Usuario Lucía Mora creado correctamente.', 'Cerrar', {
      duration: 5000,
    });
    expect(rowNames()).toContain('Lucía Mora');
  });

  describe('activate / deactivate (HU-03)', () => {
    let confirmed: boolean;
    let dialogOpen: MockInstance<MatDialog['open']>;
    let notice: MockInstance<MatSnackBar['open']>;

    const rowOf = (name: string) =>
      Array.from(element.querySelectorAll('tbody tr')).find((row) =>
        row.textContent?.includes(name),
      ) as HTMLTableRowElement;
    const actionOf = (name: string) =>
      rowOf(name).querySelector<HTMLButtonElement>('button.status-action')!;

    beforeEach(() => {
      confirmed = true;
      dialogOpen = vi
        .spyOn(TestBed.inject(MatDialog), 'open')
        .mockImplementation(
          () => ({ afterClosed: () => of(confirmed) }) as ReturnType<MatDialog['open']>,
        );
      notice = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
      // The logged-in administrator is user 3 ("Administrador Rehab")
      logInForTest('ADMINISTRATOR', 'Administrador Rehab', 3);
    });

    it('offers to deactivate active accounts and to activate inactive ones', async () => {
      await render();

      expect(element.querySelector('thead')?.textContent).toContain('Acciones');
      expect(actionOf('Ricardo Salinas').getAttribute('aria-label')).toBe(
        'Desactivar cuenta de Ricardo Salinas',
      );
      expect(actionOf('Ricardo Salinas').textContent).toContain('lock');
      expect(actionOf('Director General').getAttribute('aria-label')).toBe(
        'Activar cuenta de Director General',
      );
      expect(actionOf('Director General').textContent).toContain('lock_open');
    });

    it('does not let administrators deactivate their own account', async () => {
      await render();
      const own = actionOf('Administrador Rehab');

      expect(own.getAttribute('aria-disabled')).toBe('true');
      expect(own.getAttribute('aria-label')).toBe('No puede desactivar su propia cuenta');
      own.click();
      expect(dialogOpen).not.toHaveBeenCalled();
    });

    it('deactivates after confirming and updates the row and the indicator', async () => {
      await render();

      actionOf('Ricardo Salinas').click();
      expect(dialogOpen.mock.calls[0][1]?.data).toMatchObject({
        title: 'Desactivar cuenta',
        message:
          '¿Desactivar la cuenta de Ricardo Salinas? No podrá iniciar sesión hasta que la reactive.',
        tone: 'danger',
      });
      const request = backend.expectOne('/api/users/1/status');
      expect(request.request.method).toBe('PATCH');
      expect(request.request.body).toEqual({ active: false });
      await fixture.whenStable();
      expect(actionOf('Ricardo Salinas').getAttribute('aria-disabled')).toBe('true');

      request.flush({ ...users[0], active: false });
      await fixture.whenStable();

      expect(rowOf('Ricardo Salinas').textContent).toContain('Inactivo');
      expect(actionOf('Ricardo Salinas').getAttribute('aria-disabled')).toBeNull();
      expect(statValues()[0]).toContain('3');
      expect(notice).toHaveBeenCalledWith('Cuenta desactivada correctamente.', 'Cerrar', {
        duration: 5000,
      });
    });

    it('activates an inactive account with a primary confirmation', async () => {
      await render();

      actionOf('Director General').click();
      expect(dialogOpen.mock.calls[0][1]?.data).toMatchObject({
        title: 'Activar cuenta',
        tone: 'primary',
      });
      const request = backend.expectOne('/api/users/4/status');
      expect(request.request.body).toEqual({ active: true });
      request.flush({ ...users[3], active: true });
      await fixture.whenStable();

      expect(rowOf('Director General').textContent).toContain('Activo');
      expect(statValues()[0]).toContain('5');
      expect(notice).toHaveBeenCalledWith('Cuenta activada correctamente.', 'Cerrar', {
        duration: 5000,
      });
    });

    it('changes nothing when the confirmation is cancelled', async () => {
      confirmed = false;
      await render();

      actionOf('Ricardo Salinas').click();
      await fixture.whenStable();

      backend.expectNone('/api/users/1/status');
      expect(rowOf('Ricardo Salinas').textContent).toContain('Activo');
      expect(notice).not.toHaveBeenCalled();
    });

    it('shows the server message when the change fails', async () => {
      await render();

      actionOf('José Montaño').click();
      backend
        .expectOne('/api/users/2/status')
        .flush(
          { title: 'Regla de negocio', detail: 'No puede desactivar su propia cuenta.' },
          { status: 422, statusText: 'Unprocessable Content' },
        );
      await fixture.whenStable();

      expect(notice).toHaveBeenCalledWith('No puede desactivar su propia cuenta.', 'Cerrar', {
        duration: 6000,
      });
      expect(rowOf('José Montaño').textContent).toContain('Activo');
      expect(actionOf('José Montaño').getAttribute('aria-disabled')).toBeNull();
    });
  });

  it('offers a retry when the list cannot be loaded', async () => {
    fixture = TestBed.createComponent(UserList);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    backend.expectOne('/api/users').flush(null, { status: 500, statusText: 'Error' });
    await fixture.whenStable();

    expect(element.textContent).toContain('No se pudieron cargar los usuarios');
    (element.querySelector('app-empty-state button') as HTMLButtonElement).click();
    backend.expectOne('/api/users').flush(users);
    await fixture.whenStable();
    expect(rowNames()).toHaveLength(5);
  });
});
