import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AccountApi } from './account-api';

describe('AccountApi', () => {
  let api: AccountApi;
  let backend: HttpTestingController;
  const body = { token: 't', password: 'Clave.2026', passwordConfirmation: 'Clave.2026' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(AccountApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('posts the emailed links to the public endpoints (MEJ-01)', () => {
    api.activate(body).subscribe();
    const activate = backend.expectOne('/api/auth/activate');
    expect(activate.request.method).toBe('POST');
    expect(activate.request.body).toEqual(body);

    api.resetPassword(body).subscribe();
    expect(backend.expectOne('/api/auth/reset-password').request.body).toEqual(body);

    api.forgotPassword('ana@rehabelite.test').subscribe();
    expect(backend.expectOne('/api/auth/forgot-password').request.body).toEqual({
      email: 'ana@rehabelite.test',
    });
  });
});
