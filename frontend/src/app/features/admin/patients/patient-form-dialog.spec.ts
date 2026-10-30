import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { ageOn, todayIsoDate } from './patient-age';
import { RegisterPatientDialog } from './register-patient-dialog';
import { fakePatient } from './patient-testing';

describe('RegisterPatientDialog', () => {
  let fixture: ComponentFixture<RegisterPatientDialog>;
  let element: HTMLElement;
  let backend: HttpTestingController;
  const dialogRef = { close: vi.fn() };

  const form = () => fixture.componentInstance['form'];
  const errors = () =>
    Array.from(element.querySelectorAll('mat-error')).map((error) => error.textContent?.trim());
  const readOnlyValue = (label: string) => {
    const field = Array.from(element.querySelectorAll('mat-form-field')).find((f) =>
      f.querySelector('mat-label')?.textContent?.includes(label),
    );
    return field?.querySelector('input')?.value;
  };

  function fillValidPatient(): void {
    form().patchValue({
      nationalId: '1710034065',
      firstName: ' Juan Carlos ',
      lastName: 'Pérez Gómez',
      birthDate: '1996-03-15',
      sex: 'MALE',
      phone: '+593 98 765 4321',
      email: 'juan.perez@correo.ec',
      address: 'Av. Ilaló y Río Pita, Valle de los Chillos',
      occupation: 'Ingeniero civil',
      educationLevel: 'Superior',
      healthInsurance: 'IESS',
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
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RegisterPatientDialog);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('has only the admission and personal data sections, admitted today', () => {
    expect(element.querySelector('h2')?.textContent).toContain('Registrar Nuevo Paciente');
    const sections = Array.from(element.querySelectorAll('app-form-section .title')).map((title) =>
      title.textContent?.trim(),
    );
    expect(sections).toEqual(['1. Datos de admisión', '2. Datos personales']);
    expect(form().controls.admissionDate.value).toBe(todayIsoDate());
    expect(element.textContent).toContain('Autoidentificación étnica (opcional)');
    expect(element.textContent).toContain('Religión (opcional)');
  });

  it('validates the form in Spanish without calling the backend', async () => {
    form().patchValue({ email: 'no-es-correo', phone: 'abc' });
    await submit();

    expect(errors()).toEqual(
      expect.arrayContaining([
        'Ingrese la cédula.',
        'Ingrese los nombres.',
        'Ingrese los apellidos.',
        'Ingrese la fecha de nacimiento.',
        'Seleccione el sexo.',
        'Use solo números, espacios y el prefijo +.',
        'Ingrese un correo electrónico válido.',
        'Ingrese la dirección.',
        'Ingrese la ocupación.',
        'Seleccione el nivel de instrucción.',
        'Seleccione el seguro de salud.',
      ]),
    );
    backend.expectNone('/api/patients');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('rejects an invalid national ID in the client, saying which rule fails', async () => {
    fillValidPatient();
    form().controls.nationalId.setValue('1710034066');
    await submit();
    expect(errors()).toContain('El dígito verificador de la cédula no es correcto.');

    form().controls.nationalId.setValue('2510000009');
    await fixture.whenStable();
    expect(errors()).toContain(
      'Los dos primeros dígitos deben ser un código de provincia (01 a 24, o 30).',
    );
    backend.expectNone('/api/patients');
  });

  it('shows the medical record number and the age as read-only values', async () => {
    form().patchValue({ nationalId: '1710034065', birthDate: '1996-03-15' });
    await fixture.whenStable();

    expect(readOnlyValue('historia clínica')).toBe('1710034065');
    expect(readOnlyValue('Edad (calculada)')).toBe(`${ageOn('1996-03-15', todayIsoDate())} años`);
    expect(element.textContent).toContain('Solo lectura');
  });

  it('checks the birth date against today and the admission date', async () => {
    fillValidPatient();
    form().patchValue({ admissionDate: '2000-01-01', birthDate: '2005-06-01' });
    await submit();

    expect(errors()).toContain('No puede ser posterior a la fecha de ingreso.');
    form().controls.admissionDate.setValue('2010-01-01');
    await fixture.whenStable();
    expect(form().controls.birthDate.valid).toBe(true);
    backend.expectNone('/api/patients');
  });

  it('registers the patient and closes with it', async () => {
    fillValidPatient();
    form().controls.religion.setValue('  ');
    await submit();

    const request = backend.expectOne('/api/patients');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      nationalId: '1710034065',
      admissionDate: todayIsoDate(),
      firstName: 'Juan Carlos',
      lastName: 'Pérez Gómez',
      birthDate: '1996-03-15',
      sex: 'MALE',
      phone: '+593 98 765 4321',
      email: 'juan.perez@correo.ec',
      address: 'Av. Ilaló y Río Pita, Valle de los Chillos',
      occupation: 'Ingeniero civil',
      educationLevel: 'Superior',
      healthInsurance: 'IESS',
      ethnicSelfIdentification: null,
      religion: null,
    });
    const patient = fakePatient(25);
    request.flush(patient);
    expect(dialogRef.close).toHaveBeenCalledWith(patient);
  });

  it('links to the existing patient when the national ID is already registered', async () => {
    fillValidPatient();
    await submit();
    backend.expectOne('/api/patients').flush(
      {
        title: 'Conflicto',
        status: 409,
        detail: 'Ya existe un paciente registrado con esta cédula.',
        existingPatientId: 12,
      },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    const notice = element.querySelector('.duplicate');
    expect(notice?.textContent).toContain('Ya existe un paciente registrado con esta cédula.');
    expect(notice?.querySelector('a')?.getAttribute('href')).toBe('/admin/patients/12');
    expect(errors()).toContain('Esta cédula ya está registrada.');
    expect(dialogRef.close).not.toHaveBeenCalled();

    // Another national ID may be free: the notice goes away
    form().controls.nationalId.setValue('0100000009');
    await fixture.whenStable();
    expect(element.querySelector('.duplicate')).toBeNull();
  });

  it('shows the Spanish detail of other server errors', async () => {
    fillValidPatient();
    await submit();
    backend
      .expectOne('/api/patients')
      .flush(
        { detail: 'La fecha de nacimiento no puede ser posterior a la fecha de ingreso.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await fixture.whenStable();

    expect(element.querySelector('.error')?.textContent).toContain(
      'La fecha de nacimiento no puede ser posterior a la fecha de ingreso.',
    );
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
