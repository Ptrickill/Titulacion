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

  it('lists the accounts through /api/users and never creates one there (MEJ-02)', () => {
    api.findAll().subscribe();
    expect(backend.expectOne('/api/users').request.method).toBe('GET');

    expect('create' in api).toBe(false);
  });

  it('resends the pending link of an account (MEJ-01, MEJ-02)', () => {
    api.resendInvitation(6).subscribe();

    const request = backend.expectOne('/api/users/6/resend-invitation');
    expect(request.request.method).toBe('POST');
  });

  it('changes the status of an account (HU-03)', () => {
    api.changeStatus(6, false).subscribe();

    const request = backend.expectOne('/api/users/6/status');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ active: false });
  });
});
