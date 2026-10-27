import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let element: HTMLElement;
  let backend: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    sessionStorage.clear();
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

  async function submit(): Promise<void> {
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('renders the login form in Spanish', () => {
    expect(element.querySelector('h2')?.textContent).toContain('Iniciar sesión');
    expect(element.querySelector('button[type=submit]')?.textContent).toContain('Ingresar');
  });

  it('does not call the backend when the form is invalid', async () => {
    await submit();

    backend.expectNone('/api/auth/login');
    expect(element.textContent).toContain('Ingrese su correo electrónico.');
  });

  it('navigates to the role panel after a successful login', async () => {
    fill('input[type=email]', 'fisio@rehabelite.test');
    fill('input[formcontrolname=password]', 'secret');
    await submit();

    backend
      .expectOne('/api/auth/login')
      .flush({ token: 'jwt-token', role: 'PHYSIOTHERAPIST', fullName: 'Ana Pérez' });

    expect(navigateByUrl).toHaveBeenCalledWith('/physio');
  });

  it('shows a Spanish message on invalid credentials', async () => {
    fill('input[type=email]', 'fisio@rehabelite.test');
    fill('input[formcontrolname=password]', 'wrong');
    await submit();

    backend.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(element.querySelector('[role=alert]')?.textContent).toContain(
      'Correo o contraseña incorrectos.',
    );
  });
});
