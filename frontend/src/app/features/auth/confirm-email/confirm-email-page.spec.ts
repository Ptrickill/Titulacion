import { BrowserPlatformLocation, Location, PlatformLocation } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmEmailPage } from './confirm-email-page';

@Component({ template: 'Inicio de sesión' })
class LoginStub {}

describe('ConfirmEmailPage (MEJ-02)', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let element: HTMLElement;

  const TOKEN = 'q3Jx7Q0mZ1a2b3c4d5e6f7g8h9i0jKlMnOpQrStUvWx';
  const confirmButton = () =>
    Array.from(element.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Confirmar mi nuevo correo'),
    );

  async function open(url: string): Promise<void> {
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
    element = harness.routeNativeElement as HTMLElement;
  }

  async function confirm(): Promise<void> {
    confirmButton()!.click();
    await harness.fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'confirmar-correo', component: ConfirmEmailPage },
          { path: 'login', component: LoginStub },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        // The real browser history (jsdom), to see history.replaceState
        { provide: PlatformLocation, useClass: BrowserPlatformLocation },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => backend.verify());

  it('does not spend the link when the page opens: it waits for the click', async () => {
    await open(`/confirmar-correo?token=${TOKEN}`);

    expect(element.querySelector('h1')?.textContent).toContain('Confirme su nuevo correo');
    expect(element.textContent).toContain('su contraseña no cambia');
    backend.expectNone('/api/auth/confirm-email');
    expect(confirmButton()).toBeTruthy();
  });

  it('protects the token like the MEJ-01 links: out of the address bar, no Referer, no storage', async () => {
    await open(`/confirmar-correo?token=${TOKEN}`);

    expect(TestBed.inject(Location).path()).toBe('/confirmar-correo');
    expect(window.location.search).toBe('');
    expect(document.head.querySelector('meta[name="referrer"]')?.getAttribute('content')).toBe(
      'no-referrer',
    );
    const stored = [sessionStorage, localStorage].flatMap((storage) =>
      Object.keys(storage).map((key) => `${key}=${storage.getItem(key)}`),
    );
    expect(stored.join(' ')).not.toContain(TOKEN);
  });

  it('confirms the new email with the token kept in memory and offers to log in', async () => {
    await open(`/confirmar-correo?token=${TOKEN}`);

    await confirm();
    const request = backend.expectOne('/api/auth/confirm-email');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ token: TOKEN });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();

    expect(element.querySelector('.success')?.textContent).toContain(
      'Su nuevo correo quedó confirmado',
    );
    const login = Array.from(element.querySelectorAll('a')).find((a) =>
      a.textContent?.includes('Ir a iniciar sesión'),
    );
    expect(login?.getAttribute('href')).toContain('/login');
  });

  it('explains an invalid or expired link', async () => {
    await open(`/confirmar-correo?token=${TOKEN}`);

    await confirm();
    backend
      .expectOne('/api/auth/confirm-email')
      .flush(
        { detail: 'El enlace no es válido o ya venció. Solicite uno nuevo.' },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.fixture.whenStable();

    expect(element.querySelector('.error')?.textContent).toContain('El enlace no es válido');
    expect(element.textContent).toContain('siga iniciando sesión con su correo anterior');
    expect(confirmButton()).toBeUndefined();
  });

  it('without a token, says the link is not valid', async () => {
    await open('/confirmar-correo');

    expect(element.querySelector('.error')?.textContent).toContain('El enlace no es válido');
  });
});
