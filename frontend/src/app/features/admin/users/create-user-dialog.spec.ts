import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { CreateUserDialog } from './create-user-dialog';
import { fakeCandidate, fakeUser } from './user-testing';

describe('CreateUserDialog', () => {
  let fixture: ComponentFixture<CreateUserDialog>;
  let element: HTMLElement;
  let backend: HttpTestingController;
  const dialogRef = { close: vi.fn() };

  const form = () => fixture.componentInstance['form'];
  const errors = () =>
    Array.from(element.querySelectorAll('mat-error')).map((error) => error.textContent?.trim());

  function fillAccount(role: 'PHYSIOTHERAPIST' | 'PATIENT'): void {
    form().patchValue({
      role,
      email: 'lucia.mora@rehabelite.test',
      fullName: 'Lucía Fernanda Mora',
      password: 'Clave.Inicial2026',
      passwordConfirmation: 'Clave.Inicial2026',
    });
  }

  async function submit(): Promise<void> {
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  beforeEach(async () => {
    dialogRef.close.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CreateUserDialog);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('validates the form in Spanish without calling the backend', async () => {
    form().patchValue({ email: 'no-es-correo', password: 'corta', passwordConfirmation: 'otra' });
    await submit();

    expect(errors()).toEqual(
      expect.arrayContaining([
        'Seleccione un rol.',
        'Ingrese un correo electrónico válido.',
        'Ingrese los nombres y apellidos.',
        'Debe tener al menos 8 caracteres.',
        'Las contraseñas no coinciden.',
      ]),
    );
    backend.expectNone('/api/users');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('requires a letter and a number in the password', async () => {
    fillAccount('PHYSIOTHERAPIST');
    form().patchValue({ password: 'soloLetras', passwordConfirmation: 'soloLetras' });
    await submit();

    expect(errors()).toContain('Debe contener al menos una letra y un número.');
  });

  it('creates a physiotherapist and closes with the new account', async () => {
    fillAccount('PHYSIOTHERAPIST');
    await submit();

    const request = backend.expectOne('/api/users');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      fullName: 'Lucía Fernanda Mora',
      email: 'lucia.mora@rehabelite.test',
      role: 'PHYSIOTHERAPIST',
      password: 'Clave.Inicial2026',
      patientId: null,
    });
    const created = fakeUser(7, 'Lucía Fernanda Mora', 'PHYSIOTHERAPIST');
    request.flush(created);

    expect(dialogRef.close).toHaveBeenCalledWith(created);
  });

  it('shows the duplicate email error next to the email field', async () => {
    fillAccount('PHYSIOTHERAPIST');
    await submit();

    backend
      .expectOne('/api/users')
      .flush(
        { status: 422, detail: 'Ya existe un usuario registrado con ese correo electrónico.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await fixture.whenStable();

    expect(errors()).toContain('Ya existe un usuario registrado con ese correo electrónico.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('requires and sends the linked patient for patient accounts', async () => {
    fillAccount('PATIENT');
    await fixture.whenStable();
    backend
      .expectOne((request) => request.url === '/api/patients/portal-candidates')
      .flush([fakeCandidate(15, 'Ortiz Vega Rosa Elena')]);
    await fixture.whenStable();

    expect(element.textContent).toContain('Paciente vinculado');
    await submit();
    expect(errors()).toContain('La cuenta de paciente debe vincularse a un paciente registrado.');
    backend.expectNone('/api/users');

    fixture.componentInstance['selectPatient'](fakeCandidate(15, 'Ortiz Vega Rosa Elena'));
    await submit();

    const request = backend.expectOne('/api/users');
    expect(request.request.body).toEqual(
      expect.objectContaining({ role: 'PATIENT', patientId: 15 }),
    );
    request.flush(fakeUser(8, 'Rosa Elena Ortiz', 'PATIENT'));
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('tells when there are no patients without a portal account', async () => {
    form().controls.role.setValue('PATIENT');
    backend.expectOne((request) => request.url === '/api/patients/portal-candidates').flush([]);
    await fixture.whenStable();

    expect(element.querySelector('.notice')?.textContent).toContain(
      'No hay pacientes registrados sin cuenta de portal.',
    );
  });

  it('fills the name from the chosen patient when it is empty', async () => {
    form().controls.role.setValue('PATIENT');
    backend.expectOne((request) => request.url === '/api/patients/portal-candidates').flush([]);

    fixture.componentInstance['selectPatient'](fakeCandidate(15, 'Ortiz Vega Rosa Elena'));

    expect(form().controls.fullName.value).toBe('Ortiz Vega Rosa Elena');
    expect(form().controls.patient.value?.id).toBe(15);
  });

  it('does not search patients for other roles', async () => {
    form().controls.role.setValue('ADMINISTRATOR');
    await fixture.whenStable();

    backend.expectNone((request) => request.url === '/api/patients/portal-candidates');
    expect(element.textContent).not.toContain('Paciente vinculado');
  });
});
