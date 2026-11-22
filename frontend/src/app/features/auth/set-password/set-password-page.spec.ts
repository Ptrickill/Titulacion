import { BrowserPlatformLocation, Location, PlatformLocation } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { SetPasswordPage } from './set-password-page';

@Component({ template: 'Inicio de sesión' })
class LoginStub {}

describe('SetPasswordPage (MEJ-01)', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let element: HTMLElement;

  const TOKEN = 'q3Jx7Q0mZ1a2b3c4d5e6f7g8h9i0jKlMnOpQrStUvWx';
  const link = (text: string) =>
    Array.from(element.querySelectorAll('a')).find((a) => a.textContent?.includes(text));
  const referrerMeta = () => document.head.querySelector('meta[name="referrer"]');

  async function open(url: string): Promise<void> {
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
    element = harness.routeNativeElement as HTMLElement;
  }

  function type(selector: string, value: string): void {
    const input = element.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submit(): Promise<void> {
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await harness.fixture.whenStable();
  }

  async function fillAndSubmit(password: string, confirmation = password): Promise<void> {
    type('#new-password', password);
    type('#password-confirmation', confirmation);
    await harness.fixture.whenStable();
    await submit();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'activar-cuenta', component: SetPasswordPage, data: { mode: 'activate' } },
          { path: 'restablecer-contrasena', component: SetPasswordPage, data: { mode: 'reset' } },
          { path: 'login', component: LoginStub },
          { path: 'olvide-contrasena', component: LoginStub },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        // The real browser history (jsdom), instead of the mock TestBed provides, to see history.replaceState
        { provide: PlatformLocation, useClass: BrowserPlatformLocation },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => backend.verify());

  describe('protecting the token', () => {
    it('removes the token from the address bar with history.replaceState and keeps it in memory', async () => {
      const replaceState = vi.spyOn(window.history, 'replaceState');

      await open(`/activar-cuenta?token=${TOKEN}`);

      expect(TestBed.inject(Location).path()).toBe('/activar-cuenta');
      expect(window.location.pathname).toBe('/activar-cuenta');
      expect(window.location.search).toBe('');
      expect(replaceState).toHaveBeenCalled();
      const lastUrl = String(replaceState.mock.calls.at(-1)![2]);
      expect(lastUrl).toContain('/activar-cuenta');
      expect(lastUrl).not.toContain(TOKEN);
      // Not in any storage either: only in the component's memory
      const stored = [sessionStorage, localStorage].flatMap((storage) =>
        Object.keys(storage).map((key) => `${key}=${storage.getItem(key)}`),
      );
      expect(stored.join(' ')).not.toContain(TOKEN);

      // The token still reaches the API from memory
      await fillAndSubmit('Clave.Segura2026');
      expect(backend.expectOne('/api/auth/activate').request.body.token).toBe(TOKEN);
      replaceState.mockRestore();
    });

    it('adds Referrer-Policy no-referrer while the page is open and removes it afterwards', async () => {
      expect(referrerMeta()).toBeNull();

      await open(`/restablecer-contrasena?token=${TOKEN}`);
      expect(referrerMeta()?.getAttribute('content')).toBe('no-referrer');

      await TestBed.inject(Router).navigateByUrl('/login');
      await harness.fixture.whenStable();
      expect(referrerMeta()).toBeNull();
    });
  });

  describe('activation', () => {
    it('shows the password rules and ticks each one as it is met', async () => {
      await open(`/activar-cuenta?token=${TOKEN}`);

      expect(element.querySelector('h1')?.textContent).toContain('Active su cuenta');
      const rules = () =>
        Array.from(element.querySelectorAll('.rules li')).map((li) => ({
          label: li.textContent?.trim(),
          met: li.classList.contains('met'),
        }));
      expect(rules().map((rule) => rule.met)).toEqual([false, false, false, false]);
      expect(rules()[0].label).toContain('De 8 a 72 caracteres');

      type('#new-password', 'clave');
      await harness.fixture.whenStable();
      expect(rules().map((rule) => rule.met)).toEqual([false, true, false, false]);

      type('#new-password', 'Clave.Segura2026');
      type('#password-confirmation', 'Clave.Segura2026');
      await harness.fixture.whenStable();
      expect(rules().every((rule) => rule.met)).toBe(true);
    });

    it('does not call the API while the password breaks the rules or differs from the confirmation', async () => {
      await open(`/activar-cuenta?token=${TOKEN}`);

      await fillAndSubmit('SoloLetrasLargas');
      expect(element.textContent).toContain('La contraseña no cumple las reglas.');
      await fillAndSubmit('Clave.Segura2026', 'Clave.Otra2026');
      expect(element.textContent).toContain('Las contraseñas no coinciden.');
      backend.expectNone('/api/auth/activate');
    });

    it('activates the account and then offers the login', async () => {
      await open(`/activar-cuenta?token=${TOKEN}`);

      await fillAndSubmit('Clave.Segura2026');
      const request = backend.expectOne('/api/auth/activate');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        token: TOKEN,
        password: 'Clave.Segura2026',
        passwordConfirmation: 'Clave.Segura2026',
      });
      request.flush(null, { status: 204, statusText: 'No Content' });
      await harness.fixture.whenStable();

      expect(element.querySelector('.success')?.textContent).toContain('Su cuenta está activa.');
      expect(link('Ir a iniciar sesión')?.getAttribute('href')).toContain('/login');
      expect(element.querySelector('form')).toBeNull();
    });

    it('explains what to do when the link was used or expired', async () => {
      await open(`/activar-cuenta?token=${TOKEN}`);

      await fillAndSubmit('Clave.Segura2026');
      backend
        .expectOne('/api/auth/activate')
        .flush(
          { status: 400, detail: 'El enlace no es válido o ya venció. Solicite uno nuevo.' },
          { status: 400, statusText: 'Bad Request' },
        );
      await harness.fixture.whenStable();

      expect(element.querySelector('.error')?.textContent).toContain(
        'El enlace no es válido o ya venció.',
      );
      expect(element.textContent).toContain(
        'Pida al administrador de la clínica que le reenvíe la invitación.',
      );
      expect(element.querySelector('form')).toBeNull();
    });

    it('shows any other API message next to the form', async () => {
      await open(`/activar-cuenta?token=${TOKEN}`);

      await fillAndSubmit('Clave.Segura2026');
      backend
        .expectOne('/api/auth/activate')
        .flush(
          { status: 400, detail: 'La contraseña no puede superar los 72 bytes.' },
          { status: 400, statusText: 'Bad Request' },
        );
      await harness.fixture.whenStable();

      expect(element.querySelector('form .error')?.textContent).toContain(
        'La contraseña no puede superar los 72 bytes.',
      );
    });

    it('opened without a token, it says the link is not valid', async () => {
      await open('/activar-cuenta');

      expect(element.querySelector('.error')?.textContent).toContain('El enlace no es válido');
      expect(element.querySelector('form')).toBeNull();
    });
  });

  describe('password reset', () => {
    it('sets the new password through the reset endpoint', async () => {
      await open(`/restablecer-contrasena?token=${TOKEN}`);

      expect(element.querySelector('h1')?.textContent).toContain('Restablezca su contraseña');
      await fillAndSubmit('Clave.Nueva2026');
      backend
        .expectOne('/api/auth/reset-password')
        .flush(null, { status: 204, statusText: 'No Content' });
      await harness.fixture.whenStable();

      expect(element.querySelector('.success')?.textContent).toContain(
        'las sesiones abiertas se cerraron',
      );
    });

    it('offers a new link when the reset link no longer works', async () => {
      await open(`/restablecer-contrasena?token=${TOKEN}`);

      await fillAndSubmit('Clave.Nueva2026');
      backend
        .expectOne('/api/auth/reset-password')
        .flush(
          { status: 400, detail: 'El enlace no es válido o ya venció. Solicite uno nuevo.' },
          { status: 400, statusText: 'Bad Request' },
        );
      await harness.fixture.whenStable();

      expect(link('Solicitar un enlace nuevo')?.getAttribute('href')).toContain(
        '/olvide-contrasena',
      );
    });
  });
});
