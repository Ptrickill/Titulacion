import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
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
