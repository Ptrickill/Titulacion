import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ForgotPasswordPage } from './forgot-password-page';

describe('ForgotPasswordPage (MEJ-01)', () => {
  let fixture: ComponentFixture<ForgotPasswordPage>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const ANSWER =
    'Si el correo corresponde a una cuenta activa, recibirá en unos minutos un enlace para restablecer la contraseña.';

  async function request(email: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement>('#forgot-email')!;
    input.value = email;
    input.dispatchEvent(new Event('input'));
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ForgotPasswordPage);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('validates the email without calling the API', async () => {
    await request('no-es-correo');

    expect(element.textContent).toContain('Ingrese un correo electrónico válido.');
    backend.expectNone('/api/auth/forgot-password');
  });

  it('sends the trimmed email and shows the same answer of the API', async () => {
    await request('  ana@rehabelite.test ');

    const call = backend.expectOne('/api/auth/forgot-password');
    expect(call.request.method).toBe('POST');
    expect(call.request.body).toEqual({ email: 'ana@rehabelite.test' });
    call.flush({ message: ANSWER }, { status: 202, statusText: 'Accepted' });
    await fixture.whenStable();

    expect(element.querySelector('.success')?.textContent).toContain(ANSWER);
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('a[href="/login"]')?.textContent).toContain(
      'Volver a iniciar sesión',
    );
  });

  it('shows the error when the request fails', async () => {
    await request('ana@rehabelite.test');

    backend
      .expectOne('/api/auth/forgot-password')
      .flush(null, { status: 500, statusText: 'Error' });
    await fixture.whenStable();

    expect(element.querySelector('.error')?.textContent).toContain(
      'No se pudo enviar la solicitud. Intente nuevamente.',
    );
  });
});
