import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { SESSION_STORAGE_KEY } from '../../../core/auth/auth-service';
import { fakeLoginResponse } from '../../../core/auth/auth-testing';
import { INVALID_CREDENTIALS_MESSAGE, Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let element: HTMLElement;
  let backend: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    sessionStorage.clear();
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Login);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  function fill(selector: string, value: string): void {
    const input = element.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submitCredentials(password = 'secret'): Promise<void> {
    fill('#login-email', ' fisio@rehabelite.test ');
    fill('#login-password', password);
    await submit();
  }

  async function submit(): Promise<void> {
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('renders the prototype form in Spanish without test credentials', () => {
    const labels = Array.from(element.querySelectorAll('label.field-label')).map((label) =>
      label.textContent?.trim(),
    );
    expect(labels).toEqual(['Correo electrónico', 'Contraseña']);
    expect(element.querySelector('h2')?.textContent).toContain('Iniciar sesión');
    expect(element.querySelector('.remember')?.textContent).toContain(
      'Recordarme en este dispositivo',
    );
    expect(element.querySelector('button[type=submit]')?.textContent).toContain(
      'Ingresar al sistema',
    );
    expect(element.textContent).not.toContain('Credenciales de prueba');
    expect(element.textContent).not.toContain('Acceso de desarrollo');
  });

  it('does not call the backend when the form is invalid', async () => {
    await submit();

    backend.expectNone('/api/auth/login');
    expect(element.textContent).toContain('Ingrese su correo electrónico.');
  });

  it('sends the trimmed email and navigates to the role panel after a successful login', async () => {
    await submitCredentials();

    const request = backend.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ email: 'fisio@rehabelite.test', password: 'secret' });
    request.flush(fakeLoginResponse('PHYSIOTHERAPIST'));

    expect(navigateByUrl).toHaveBeenCalledWith('/physio');
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toContain('jwt-token');
  });

  it('remembers the session on this device when the checkbox is checked', async () => {
    element.querySelector<HTMLInputElement>('.remember input')!.click();
    await submitCredentials();

    backend.expectOne('/api/auth/login').flush(fakeLoginResponse('ADMINISTRATOR'));

    expect(navigateByUrl).toHaveBeenCalledWith('/admin');
    expect(localStorage.getItem(SESSION_STORAGE_KEY)).toContain('jwt-token');
  });

  it('shows only the generic message on invalid credentials', async () => {
    await submitCredentials('wrong');

    backend
      .expectOne('/api/auth/login')
      .flush(
        { title: 'Inicio de sesión fallido', status: 401, detail: INVALID_CREDENTIALS_MESSAGE },
        { status: 401, statusText: 'Unauthorized' },
      );
    await fixture.whenStable();

    expect(element.querySelector('[role=alert]')?.textContent).toContain(
      INVALID_CREDENTIALS_MESSAGE,
    );
    expect(element.querySelectorAll('mat-error')).toHaveLength(0);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('explains when the server cannot be reached', async () => {
    await submitCredentials();

    backend.expectOne('/api/auth/login').error(new ProgressEvent('error'), { status: 0 });
    await fixture.whenStable();

    expect(element.querySelector('[role=alert]')?.textContent).toContain(
      'No se pudo conectar con el servidor.',
    );
  });
});
