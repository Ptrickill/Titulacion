import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth-service';
import { logInForTest } from '../../core/auth/auth-testing';
import { Role } from '../../core/auth/role';
import { AppShell } from './app-shell';

describe('AppShell', () => {
  let fixture: ComponentFixture<AppShell>;
  let element: HTMLElement;

  async function renderAs(role: Role): Promise<void> {
    logInForTest(role);
    fixture = TestBed.createComponent(AppShell);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  const menuLabels = () =>
    Array.from(element.querySelectorAll('.nav-item span')).map((span) => span.textContent?.trim());

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => fixture.destroy());

  it('shows the physiotherapist menu from the prototype', async () => {
    await renderAs('PHYSIOTHERAPIST');

    expect(menuLabels()).toEqual([
      'Inicio',
      'Gestión de Pacientes',
      'Historial Clínico',
      'Línea de Tiempo',
      'Asistente Clínico IA',
    ]);
    expect(element.querySelector('.panel-label')?.textContent?.trim()).toBe(
      'Panel de especialistas',
    );
  });

  it('shows the administrator, director and patient menus', async () => {
    await renderAs('ADMINISTRATOR');
    expect(menuLabels()).toEqual([
      'Inicio',
      'Gestión de Usuarios',
      'Registro de Pacientes',
      'Reportes Consolidados',
    ]);
    fixture.destroy();

    await renderAs('OPERATIONS_DIRECTOR');
    expect(menuLabels()).toEqual(['Reportes Consolidados']);
    fixture.destroy();

    await renderAs('PATIENT');
    expect(menuLabels()).toEqual(['Mi Tratamiento']);
  });

  it('shows user name, role and the date in Spanish in the header', async () => {
    await renderAs('OPERATIONS_DIRECTOR');

    expect(element.querySelector('.user-name')?.textContent).toBe('Usuario de prueba');
    expect(element.querySelector('.role-badge')?.textContent?.trim()).toBe('Dirección operativa');
    expect(element.querySelector('.avatar')?.textContent?.trim()).toBe('UD');
    expect(element.querySelector('.date time')?.textContent).toMatch(
      /^[a-záéíóúñ]+, \d{1,2} de [a-záéíóúñ]+ de \d{4}$/,
    );
  });

  it('logs out and returns to /login', async () => {
    await renderAs('PATIENT');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    element.querySelector<HTMLButtonElement>('.logout')!.click();

    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
