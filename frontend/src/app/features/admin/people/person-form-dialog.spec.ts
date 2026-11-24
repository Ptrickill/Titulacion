import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { provideEcDates } from '../../../shared/forms/ec-date-adapter';
import { FAKE_LOCATIONS, fakePatient, legacyAddress } from '../patients/patient-testing';
import { PersonMatch } from './people-api';
import { fakeAccount, fakeMatch, fakeStaff, legacyStaff } from './people-testing';
import { PersonFormDialog } from './person-form-dialog';

describe('PersonFormDialog (MEJ-03, MEJ-02)', () => {
  let fixture: ComponentFixture<PersonFormDialog>;
  let element: HTMLElement;
  let backend: HttpTestingController;
  const dialogRef = { close: vi.fn() };

  const component = () => fixture.componentInstance as unknown as Record<string, any>;
  const errors = () =>
    Array.from(element.querySelectorAll('mat-error'))
      .map((error) => error.textContent?.trim())
      .filter(Boolean);
  const selectedStep = () => component()['selectedIndex']() as number;
  const button = (selector: string) => element.querySelector<HTMLButtonElement>(selector)!;
  const stepContent = () =>
    element.querySelector('.mat-horizontal-stepper-content-current') as HTMLElement;
  const stepLabels = () =>
    Array.from(element.querySelectorAll('.mat-step-label')).map((label) =>
      label.textContent?.trim(),
    );
  /** Real time: fake timers would also stop the change detection of the zoneless application. */
  const pause = () => new Promise((resolve) => setTimeout(resolve, 350));

  async function create(data: unknown = null): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        ...provideEcDates(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PersonFormDialog);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    backend.expectOne('/api/catalogs/locations').flush(FAKE_LOCATIONS);
    await fixture.whenStable();
  }

  async function next(): Promise<void> {
    button('button.next').click();
    await fixture.whenStable();
  }

  async function type(selector: string, value: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  }

  /** Step 0: the type of person and, for the staff, the role. */
  async function chooseType(type: 'PATIENT' | 'STAFF', role?: string): Promise<void> {
    component()['kind'].patchValue({ type, role: role ?? null });
    await fixture.whenStable();
    await next();
  }

  /** Answers the pending GET /api/people/matches of a lookup. */
  function answerMatches(param: 'nationalId' | 'email', matches: PersonMatch[] = []) {
    const request = backend.expectOne(
      (r) => r.url === '/api/people/matches' && r.params.has(param),
    );
    request.flush(matches);
    return request;
  }

  /** National ID with a valid check digit that nobody has. */
  async function passIdentification(nationalId = '1710034065'): Promise<void> {
    await type('#person-national-id', nationalId);
    await pause();
    answerMatches('nationalId');
    await fixture.whenStable();
    await next();
  }

  /** Fills step 2; the email check answers with the given matches. */
  async function fillPersonal(emailMatches: PersonMatch[] = []): Promise<void> {
    component()['personal'].patchValue({
      firstName: '  juan   CARLOS ',
      lastName: 'PÉREZ de la torre',
      birthDate: '1996-03-15',
      sex: 'MALE',
      phone: '0987654321',
      email: 'Juan.Perez@correo.ec',
    });
    component()['personal'].controls.address.patchValue({
      provinceCode: '17',
      cantonCode: '1701',
      parishCode: '170156',
      mainStreet: 'Av. Ilaló',
      streetNumber: 'N12-34',
      crossStreet: 'Río Pita',
      reference: 'Frente al parque central',
    });
    await pause();
    answerMatches('email', emailMatches);
    await fixture.whenStable();
  }

  function fillAdmission(): void {
    component()['admission'].patchValue({
      occupation: 'Ingeniero civil',
      educationLevel: 'Superior',
      hasHealthInsurance: false,
    });
  }

  beforeEach(() => dialogRef.close.mockReset());
  afterEach(() => backend.verify());

  describe('step 0: the type of person', () => {
    beforeEach(async () => create());

    it('opens on the type step, without errors, and asks first whom to register', () => {
      expect(element.querySelector('.title')?.textContent).toContain('Registrar usuario');
      expect(element.querySelector('.subtitle')?.textContent).toContain('Paciente o personal');
      expect(stepLabels()[0]).toBe('Tipo de usuario');
      expect(stepContent().textContent).toContain('¿A quién va a registrar?');
      expect(errors()).toEqual([]);
      expect(element.querySelector('.choice-error')).toBeNull();
    });

    it('does not move on without a type, nor without the role of a staff member', async () => {
      await next();
      expect(selectedStep()).toBe(0);
      expect(stepContent().textContent).toContain('Seleccione el tipo de usuario.');

      component()['kind'].controls.type.setValue('STAFF');
      await fixture.whenStable();
      expect(stepContent().textContent).toContain('Rol de la cuenta');
      await next();
      expect(selectedStep()).toBe(0);
      expect(stepContent().textContent).toContain('Seleccione el rol.');

      const roles = Array.from(stepContent().querySelectorAll('.role-choice button')).map((b) =>
        b.textContent?.trim(),
      );
      expect(roles).toEqual(['Fisioterapeuta', 'Administración', 'Dirección operativa']);
      (stepContent().querySelectorAll('.role-choice button')[0] as HTMLButtonElement).click();
      await fixture.whenStable();
      expect(element.querySelector('.subtitle')?.textContent).toContain(
        'Personal · Fisioterapeuta',
      );
      await next();
      expect(selectedStep()).toBe(1);
    });

    it('a patient has five steps and a staff member four, without the admission data', async () => {
      component()['kind'].controls.type.setValue('PATIENT');
      await fixture.whenStable();
      expect(stepLabels()).toEqual([
        'Tipo de usuario',
        'Identificación',
        'Datos personales y contacto',
        'Datos de admisión',
        'Revisión',
      ]);

      component()['kind'].patchValue({ type: 'STAFF', role: 'ADMINISTRATOR' });
      await fixture.whenStable();
      expect(stepLabels()).toEqual([
        'Tipo de usuario',
        'Identificación',
        'Datos personales y contacto',
        'Revisión',
      ]);
    });
  });

  describe('patient', () => {
    beforeEach(async () => {
      await create();
      await chooseType('PATIENT');
    });

    it('does not move on with an invalid step and only then shows its errors', async () => {
      expect(errors()).toEqual([]);
      await next();

      expect(selectedStep()).toBe(1);
      expect(errors()).toEqual(['Ingrese la cédula.']);
    });

    it('validates the national ID while typing and shows the medical record number live', async () => {
      await type('#person-national-id', '1710034066');

      expect(errors()).toContain('El dígito verificador de la cédula no es correcto.');
      expect(element.querySelector<HTMLInputElement>('.record-number')?.value).toBe('1710034066');
      backend.expectNone((request) => request.url === '/api/people/matches');
    });

    it('warns in step 1 when a patient or a staff member already has the national ID', async () => {
      await type('#person-national-id', '1710034065');
      await pause();
      const lookup = answerMatches('nationalId', [
        fakeMatch({ type: 'STAFF', id: 4, fullName: 'Valeria Andrade Mora' }),
      ]);
      expect(lookup.request.params.get('nationalId')).toBe('1710034065');
      await fixture.whenStable();

      const notice = element.querySelector('.duplicate');
      expect(notice?.textContent).toContain(
        'Ya existe un miembro del personal registrado con esta cédula: Valeria Andrade Mora',
      );
      expect(notice?.querySelector('a')?.getAttribute('href')).toBe('/admin/people/staff/4');
      await next();
      expect(selectedStep()).toBe(1);
    });

    it('links to the existing patient and moves on when nobody has the national ID', async () => {
      await type('#person-national-id', '1710034065');
      await pause();
      answerMatches('nationalId', [fakeMatch()]);
      await fixture.whenStable();
      expect(element.querySelector('.duplicate a')?.getAttribute('href')).toBe(
        '/admin/people/patients/15',
      );

      await type('#person-national-id', '1702000165');
      await pause();
      answerMatches('nationalId');
      await fixture.whenStable();
      expect(element.querySelector('.duplicate')).toBeNull();
      await next();
      expect(selectedStep()).toBe(2);
    });

    it('reads the birth date as dd/mm/aaaa, shows the age and rejects dates that do not exist', async () => {
      await passIdentification();
      const birthDate = component()['personal'].controls.birthDate;

      await type('#person-birth-date', '15/03/1996');
      expect(birthDate.value).toBe('1996-03-15');
      expect(element.querySelector('.age-value')?.textContent).toMatch(/\d+ años/);

      await type('#person-birth-date', '31/02/2026');
      expect(birthDate.hasError('invalidDate')).toBe(true);
      expect(errors()).toContain('Ingrese una fecha válida en formato dd/mm/aaaa.');
    });

    it('validates the phone with the same message as the API', async () => {
      await passIdentification();

      await type('#person-phone', '+593 98 765 4321');

      expect(errors()).toContain(
        'Ingrese un celular (09XXXXXXXX) o un teléfono fijo con código de provincia',
      );
    });

    it('fills the canton list from the chosen province and clears it on change', async () => {
      await passIdentification();
      const address = component()['personal'].controls.address;

      address.controls.provinceCode.setValue('17');
      address.controls.cantonCode.setValue('1701');
      address.controls.provinceCode.setValue('09');
      await fixture.whenStable();

      expect(address.controls.cantonCode.value).toBe('');
    });

    it('only warns when other patients share the email, and still moves on', async () => {
      await passIdentification();
      await fillPersonal([
        fakeMatch({ id: 20, fullName: 'Rosa Elena Ortiz Vega', field: 'email' }),
      ]);

      expect(stepContent().querySelector('.shared-email')?.textContent).toContain(
        'Otro paciente usa este correo: Rosa Elena Ortiz Vega',
      );
      expect(component()['personal'].controls.email.valid).toBe(true);
      await next();
      expect(selectedStep()).toBe(3);
    });

    it('stops at the email of a staff member', async () => {
      await passIdentification();
      await fillPersonal([
        fakeMatch({ type: 'STAFF', id: 4, fullName: 'Valeria Andrade Mora', field: 'email' }),
      ]);

      expect(stepContent().querySelector('.email-owner')?.textContent).toContain(
        'Este correo ya es de un miembro del personal: Valeria Andrade Mora',
      );
      await next();
      expect(selectedStep()).toBe(2);
      expect(errors()).toContain('El correo ya está registrado.');
    });

    it('answers the health insurance with Sí or No and asks for it before moving on', async () => {
      await passIdentification();
      await fillPersonal();
      await next();
      expect(selectedStep()).toBe(3);

      component()['admission'].patchValue({
        occupation: 'Ingeniero civil',
        educationLevel: 'Superior',
      });
      await next();
      expect(selectedStep()).toBe(3);
      expect(stepContent().textContent).toContain('Indique si tiene seguro de salud.');
    });

    it('reviews every value with "Editar" per section and registers the patient', async () => {
      await passIdentification();
      await fillPersonal();
      await next();
      fillAdmission();
      await next();
      expect(selectedStep()).toBe(4);

      const review = stepContent().textContent ?? '';
      expect(review).toContain('1710034065');
      expect(review).toContain('Pérez de la Torre');
      expect(review).toContain(
        'Av. Ilaló N12-34 y Río Pita, Conocoto, Distrito Metropolitano de Quito, Pichincha. Referencia: Frente al parque central',
      );
      expect(review).toMatch(/Seguro de salud\s*No/);
      expect(stepContent().querySelectorAll('button.edit')).toHaveLength(3);

      button('button.submit').click();
      await fixture.whenStable();
      const request = backend.expectOne('/api/patients');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual(
        expect.objectContaining({
          nationalId: '1710034065',
          firstName: 'Juan Carlos',
          lastName: 'Pérez de la Torre',
          hasHealthInsurance: false,
        }),
      );
      const created = fakePatient(7);
      request.flush(created);
      expect(dialogRef.close).toHaveBeenCalledWith({ type: 'PATIENT', patient: created });
    });

    it('goes back to step 1 when the server says the national ID is taken', async () => {
      await passIdentification();
      await fillPersonal();
      await next();
      fillAdmission();
      await next();

      button('button.submit').click();
      await fixture.whenStable();
      backend.expectOne('/api/patients').flush(
        {
          status: 409,
          detail: 'Esta cédula pertenece a un miembro del personal registrado.',
          existingStaffId: 4,
          field: 'nationalId',
        },
        { status: 409, statusText: 'Conflict' },
      );
      await fixture.whenStable();

      expect(selectedStep()).toBe(1);
      expect(element.querySelector('.duplicate a')?.getAttribute('href')).toBe(
        '/admin/people/staff/4',
      );
    });
  });

  describe('staff', () => {
    beforeEach(async () => {
      await create();
      await chooseType('STAFF', 'PHYSIOTHERAPIST');
    });

    it('asks the national ID without a medical record number', () => {
      expect(selectedStep()).toBe(1);
      expect(element.querySelector('.record-number')).toBeNull();
    });

    it('stops at an email that a patient already uses', async () => {
      await passIdentification();
      await fillPersonal([
        fakeMatch({ id: 20, fullName: 'Rosa Elena Ortiz Vega', field: 'email' }),
      ]);

      expect(stepContent().querySelector('.email-owner')?.textContent).toContain(
        'Este correo ya es de un paciente: Rosa Elena Ortiz Vega',
      );
      await next();
      expect(selectedStep()).toBe(2);
    });

    it('reviews the role and registers the staff member with its account', async () => {
      await passIdentification();
      await fillPersonal();
      await next();
      expect(selectedStep()).toBe(3);

      const review = stepContent().textContent ?? '';
      expect(review).toMatch(/Rol\s*Fisioterapeuta/);
      expect(review).not.toContain('Datos de admisión');
      expect(stepContent().querySelector('.account-note')?.textContent).toContain(
        'se enviará a juan.perez@correo.ec el enlace para activarla',
      );

      button('button.submit').click();
      await fixture.whenStable();
      const request = backend.expectOne('/api/staff');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        role: 'PHYSIOTHERAPIST',
        nationalId: '1710034065',
        firstName: 'Juan Carlos',
        lastName: 'Pérez de la Torre',
        birthDate: '1996-03-15',
        sex: 'MALE',
        phone: '0987654321',
        email: 'Juan.Perez@correo.ec',
        address: {
          provinceCode: '17',
          cantonCode: '1701',
          parishCode: '170156',
          mainStreet: 'Av. Ilaló',
          streetNumber: 'N12-34',
          crossStreet: 'Río Pita',
          reference: 'Frente al parque central',
        },
      });
      const created = fakeStaff(9);
      request.flush(created);
      expect(dialogRef.close).toHaveBeenCalledWith({ type: 'STAFF', staff: created });
    });

    it('shows the 503 of the activation email in the dialog and goes back to the email when it is taken', async () => {
      await passIdentification();
      await fillPersonal();
      await next();

      button('button.submit').click();
      await fixture.whenStable();
      backend.expectOne('/api/staff').flush(
        {
          status: 503,
          detail: 'No se pudo enviar el correo de activación. Intente nuevamente en unos minutos.',
        },
        { status: 503, statusText: 'Service Unavailable' },
      );
      await fixture.whenStable();
      expect(element.querySelector('.error')?.textContent).toContain(
        'No se pudo enviar el correo de activación',
      );
      expect(dialogRef.close).not.toHaveBeenCalled();

      button('button.submit').click();
      await fixture.whenStable();
      backend.expectOne('/api/staff').flush(
        {
          status: 409,
          detail: 'Este correo pertenece a un paciente registrado.',
          existingPatientId: 20,
          field: 'email',
        },
        { status: 409, statusText: 'Conflict' },
      );
      await fixture.whenStable();
      expect(selectedStep()).toBe(2);
      expect(element.querySelector('.email-owner a')?.getAttribute('href')).toBe(
        '/admin/people/patients/20',
      );
    });
  });

  describe('editing', () => {
    it('fills the patient steps, keeps the national ID read-only and warns about the data to complete', async () => {
      await create({
        patient: fakePatient(15, {
          phone: '+593 98 765 4321',
          address: legacyAddress('Calle Antigua N10-01, Quito'),
          needsCompletion: true,
        }),
      });

      expect(element.querySelector('.title')?.textContent).toContain('Editar usuario');
      expect(stepLabels()[0]).toBe('Identificación');
      expect(element.querySelector('.completion')?.textContent).toContain('datos por completar');
      expect(element.querySelector<HTMLInputElement>('#person-national-id')?.disabled).toBe(true);
      expect(errors()).toEqual([]);
      component()['goTo']('review');
      await fixture.whenStable();

      button('button.submit').click();
      await fixture.whenStable();
      backend.expectNone('/api/patients/15');
      expect(selectedStep()).toBe(1);
    });

    it('saves a patient with PUT', async () => {
      await create({ patient: fakePatient(15) });

      component()['goTo']('review');
      await fixture.whenStable();
      button('button.submit').click();
      await fixture.whenStable();

      const request = backend.expectOne('/api/patients/15');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body.nationalId).toBeUndefined();
      request.flush(fakePatient(15));
      expect(dialogRef.close).toHaveBeenCalledWith({ type: 'PATIENT', patient: fakePatient(15) });
    });

    it('explains what the account does with a new email', async () => {
      await create({
        patient: fakePatient(15, { portalAccount: fakeAccount({ status: 'ACTIVE' }) }),
      });

      component()['personal'].controls.email.setValue('nuevo@correo.ec');
      await pause();
      answerMatches('email');
      await fixture.whenStable();

      expect(element.querySelector('.email-change')?.textContent).toContain(
        'Se enviará un enlace de confirmación al nuevo correo y un aviso al correo anterior',
      );
    });

    it('lets a staff record to be completed receive its national ID and saves it with PUT', async () => {
      await create({ staff: legacyStaff(5) });

      expect(element.querySelector('.subtitle')?.textContent).toContain(
        'Personal · Dirección operativa',
      );
      expect(element.querySelector('.completion')?.textContent).toContain(
        'Este registro tiene datos por completar',
      );
      const nationalId = element.querySelector<HTMLInputElement>('#person-national-id')!;
      expect(nationalId.disabled).toBe(false);
      expect(stepLabels()).toEqual(['Identificación', 'Datos personales y contacto', 'Revisión']);

      await type('#person-national-id', '1702000322');
      await pause();
      answerMatches('nationalId');
      component()['personal'].patchValue({
        lastName: 'Herrera Vásconez',
        birthDate: '1979-11-25',
        sex: 'FEMALE',
        phone: '022345201',
      });
      component()['personal'].controls.address.patchValue({
        provinceCode: '17',
        cantonCode: '1701',
        parishCode: '170112',
        mainStreet: 'Pasaje Ficticio',
        crossStreet: 'Calle de Prueba',
        reference: 'Frente a la plaza',
      });
      component()['goTo']('review');
      await fixture.whenStable();
      button('button.submit').click();
      await fixture.whenStable();

      const request = backend.expectOne('/api/staff/5');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body.nationalId).toBe('1702000322');
      expect(request.request.body.role).toBeUndefined();
      request.flush(fakeStaff(5));
      expect(dialogRef.close).toHaveBeenCalled();
    });
  });
});
