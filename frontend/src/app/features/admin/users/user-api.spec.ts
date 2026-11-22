import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UserApi } from './user-api';

describe('UserApi', () => {
  let api: UserApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(UserApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists and creates accounts through /api/users', () => {
    api.findAll().subscribe();
    expect(backend.expectOne('/api/users').request.method).toBe('GET');

    const body = {
      email: 'a@rehabelite.test',
      fullName: 'A',
      role: 'ADMINISTRATOR' as const,
      patientId: null,
    };
    api.create(body).subscribe();
    const create = backend.expectOne('/api/users');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(body);
  });

  it('resends the invitation of a pending account (MEJ-01)', () => {
    api.resendInvitation(6).subscribe();

    const request = backend.expectOne('/api/users/6/resend-invitation');
    expect(request.request.method).toBe('POST');
  });

  it('searches patients without portal account', () => {
    api.findPortalCandidates('ortiz').subscribe();

    const request = backend.expectOne((r) => r.url === '/api/patients/portal-candidates');
    expect(request.request.params.get('query')).toBe('ortiz');
  });
});
