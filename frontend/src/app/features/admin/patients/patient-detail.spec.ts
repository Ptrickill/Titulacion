import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { fakeAccount } from '../../../shared/people/people-testing';
import { fakeUser } from '../../../shared/people/user-testing';
import { PatientDetail } from './patient-detail';
import { fakePatient } from '../../../shared/people/patient-testing';

describe('PatientDetail', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let element: HTMLElement;

  async function open(id: string): Promise<HTMLElement> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/admin/patients/${id}`, PatientDetail);
    element = harness.routeNativeElement as HTMLElement;
    return element;
  }

  async function loaded(patient = fakePatient(25)): Promise<void> {
    await open(String(patient.id));
    backend.expectOne(`/api/patients/${patient.id}`).flush(patient);
    await settle();
  }

  async function settle(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  const button = (text: string) =>
    Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes(text));

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'admin/patients/:id', component: PatientDetail }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('shows the admission and personal data with the calculated age', async () => {
    await loaded();

    expect(element.querySelector('h1')?.textContent).toContain('Juan Carlos Pérez Gómez');
    const text = element.textContent!;
    expect(text).toContain('Historia clínica N.º 1710034065');
    expect(text).toContain('15/03/1996');
    expect(text).toContain('(30 años)');
    expect(text).toContain('Masculino');
    expect(text).toContain('No especificada');
    expect(element.querySelector('a[href="/admin/patients"]')).toBeTruthy();
  });

  it('edits the data and shows the saved record with its last update', async () => {
    await loaded();
    expect(element.textContent).toContain('Sin cambios desde el registro');

    const saved = fakePatient(25, { phone: '0991112233', updatedAt: '2026-10-30T16:45:00Z' });
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of({ type: 'PATIENT', patient: saved }),
    } as never);
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    button('Editar datos')!.click();
    await settle();

    expect(element.textContent).toContain('0991112233');
    expect(element.textContent).not.toContain('Sin cambios desde el registro');
    expect(snackBar).toHaveBeenCalledWith(
      'Datos del paciente actualizados correctamente.',
      'Cerrar',
      { duration: 5000 },
    );
  });

  describe('portal access (MEJ-02)', () => {
    it('creates the account with the email and the names of the record, without typing them', async () => {
      await loaded();
      const card = element.querySelector('app-person-account-card')!;
      expect(card.textContent).toContain('Acceso al portal');
      expect(card.textContent).toContain('Se creará con su correo juan.perez@correo.ec');
      expect(card.querySelector('input')).toBeNull();

      const confirm = vi
        .spyOn(TestBed.inject(MatDialog), 'open')
        .mockReturnValue({ afterClosed: () => of(true) } as never);
      const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
      button('Crear acceso al portal')!.click();
      await settle();

      expect(confirm).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({
          data: expect.objectContaining({
            message:
              'Se creará la cuenta de Juan Carlos Pérez Gómez y se enviará el enlace de activación a juan.perez@correo.ec.',
          }),
        }),
      );
      const request = backend.expectOne('/api/patients/25/portal-account');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toBeNull();
      request.flush(fakeAccount());
      await settle();

      expect(card.textContent).toContain('Pendiente de activación');
      expect(button('Reenviar invitación')).toBeTruthy();
      expect(button('Crear acceso al portal')).toBeUndefined();
      expect(snackBar).toHaveBeenCalledWith(
        'Enviamos un enlace de activación a juan.perez@correo.ec',
        'Cerrar',
        { duration: 6000 },
      );
    });

    it('does nothing when the confirmation is cancelled', async () => {
      await loaded();
      vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
        afterClosed: () => of(false),
      } as never);

      button('Crear acceso al portal')!.click();
      await settle();

      backend.expectNone('/api/patients/25/portal-account');
    });

    it('shows the status of an existing account and resends a pending invitation', async () => {
      await loaded(fakePatient(25, { portalAccount: fakeAccount({ userId: 31 }) }));
      const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');

      button('Reenviar invitación')!.click();
      const request = backend.expectOne('/api/users/31/resend-invitation');
      expect(request.request.method).toBe('POST');
      request.flush(
        fakeUser(31, 'Juan Carlos Pérez Gómez', 'PATIENT', { status: 'PENDING_ACTIVATION' }),
      );
      await settle();

      expect(snackBar).toHaveBeenCalledWith(
        'Enviamos un nuevo enlace de activación a juan.perez@correo.ec',
        'Cerrar',
        { duration: 6000 },
      );
    });

    it('an active account has nothing to resend, unless a new email waits for confirmation', async () => {
      await loaded(fakePatient(25, { portalAccount: fakeAccount({ status: 'ACTIVE' }) }));
      expect(element.querySelector('app-person-account-card')?.textContent).toContain('Activa');
      expect(button('Reenviar')).toBeUndefined();
      // The account is managed here: "Gestión de Usuarios" no longer exists (MEJ-02)
      expect(element.querySelector('a[href="/admin/users"]')).toBeNull();
      expect(button('Desactivar cuenta')).toBeTruthy();
      expect(element.textContent).toContain('Último acceso: Nunca');
    });

    it('deactivates the portal account after confirming', async () => {
      await loaded(
        fakePatient(25, { portalAccount: fakeAccount({ userId: 31, status: 'ACTIVE' }) }),
      );
      vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
        afterClosed: () => of(true),
      } as never);

      button('Desactivar cuenta')!.click();
      await settle();
      const request = backend.expectOne('/api/users/31/status');
      expect(request.request.body).toEqual({ active: false });
      request.flush(
        fakeUser(31, 'Juan Carlos Pérez Gómez', 'PATIENT', { active: false, status: 'INACTIVE' }),
      );
      await settle();

      expect(element.querySelector('app-person-account-card')?.textContent).toContain('Inactiva');
      expect(button('Activar cuenta')).toBeTruthy();
    });

    it('offers to resend the verification of a pending new email', async () => {
      await loaded(
        fakePatient(25, {
          portalAccount: fakeAccount({ status: 'ACTIVE', emailChangePending: true }),
        }),
      );

      expect(element.textContent).toContain('Cambio de correo por confirmar');
      expect(button('Reenviar verificación')).toBeTruthy();
    });

    it('shows the API message when the account cannot be created', async () => {
      await loaded();
      vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
        afterClosed: () => of(true),
      } as never);
      const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');

      button('Crear acceso al portal')!.click();
      await settle();
      backend
        .expectOne('/api/patients/25/portal-account')
        .flush(
          { detail: 'Ya existe una cuenta con el correo de este paciente.' },
          { status: 409, statusText: 'Conflict' },
        );
      await settle();

      expect(snackBar).toHaveBeenCalledWith(
        'Ya existe una cuenta con el correo de este paciente.',
        'Cerrar',
        { duration: 6000 },
      );
    });
  });

  it('tells when the patient does not exist', async () => {
    await open('999');
    backend
      .expectOne('/api/patients/999')
      .flush({ detail: 'El paciente no existe.' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(element.textContent).toContain('El paciente no existe');
    expect(element.querySelector('a[href="/admin/patients"]')).toBeTruthy();
  });

  it('does not call the API for an id that is not a number', async () => {
    await open('abc');

    backend.expectNone(() => true);
    expect(element.textContent).toContain('El paciente no existe');
  });
});
