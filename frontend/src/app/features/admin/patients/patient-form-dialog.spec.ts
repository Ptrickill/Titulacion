import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { provideEcDates } from '../../../shared/forms/ec-date-adapter';
import { PatientFormDialog } from './patient-form-dialog';
import { FAKE_LOCATIONS, fakePatient, legacyAddress } from './patient-testing';

describe('PatientFormDialog (MEJ-03)', () => {
  let fixture: ComponentFixture<PatientFormDialog>;
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
    fixture = TestBed.createComponent(PatientFormDialog);
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

  /**
   * Types the national ID and waits for the 300 ms pause of the check. Real time: fake timers would also stop the
   * change detection of the zoneless application.
   */
  async function typeNationalId(nationalId: string): Promise<void> {
    await type('#patient-national-id', nationalId);
    await new Promise((resolve) => setTimeout(resolve, 350));
  }

  /** National ID with a valid check digit that no patient has (the search answers without a match). */
  async function passIdentification(nationalId = '1710034065'): Promise<void> {
    await typeNationalId(nationalId);
    backend
      .expectOne((request) => request.url === '/api/patients')
      .flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    await fixture.whenStable();
    await next();
  }

  function fillPersonal(): void {
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

  describe('registration', () => {
    beforeEach(async () => create());

    it('opens on the first step with the new titles and without any error', () => {
      expect(element.querySelector('.title')?.textContent).toContain('Registrar usuario');
      expect(element.querySelector('.subtitle')?.textContent).toContain('Paciente');
      expect(
        Array.from(element.querySelectorAll('.mat-step-label')).map((label) =>
          label.textContent?.trim(),
        ),
      ).toEqual(['Identificación', 'Datos personales y contacto', 'Datos de admisión', 'Revisión']);
      expect(selectedStep()).toBe(0);
      expect(errors()).toEqual([]);
      expect(element.querySelector('.duplicate')).toBeNull();
      expect(button('button.previous').disabled).toBe(true);
    });

    it('does not move on with an invalid step and only then shows its errors', async () => {
      await next();

      expect(selectedStep()).toBe(0);
      expect(errors()).toEqual(['Ingrese la cédula.']);
    });

    it('validates the national ID while typing and shows the medical record number live', async () => {
      await type('#patient-national-id', '1710034066');

      expect(errors()).toContain('El dígito verificador de la cédula no es correcto.');
      expect(element.querySelector<HTMLInputElement>('.record-number')?.value).toBe('1710034066');
      backend.expectNone((request) => request.url === '/api/patients');
    });

    it('warns in step 1 when the national ID is already registered, before the rest is filled', async () => {
      await typeNationalId('1710034065');
      const search = backend.expectOne((request) => request.url === '/api/patients');
      expect(search.request.params.get('q')).toBe('1710034065');
      search.flush({
        content: [
          // A national ID that only starts the same way is not the same patient
          { id: 3, nationalId: '1710034066', firstName: 'Otra', lastName: 'Persona' },
          { id: 15, nationalId: '1710034065', firstName: 'Juan Carlos', lastName: 'Pérez Gómez' },
        ],
        page: 0,
        size: 10,
        totalElements: 2,
        totalPages: 1,
      });
      await fixture.whenStable();

      const notice = element.querySelector('.duplicate');
      expect(notice?.textContent).toContain(
        'Ya existe un paciente registrado con esta cédula: Juan Carlos Pérez Gómez',
      );
      expect(notice?.querySelector('a')?.getAttribute('href')).toBe('/admin/patients/15');
      await next();
      expect(selectedStep()).toBe(0);
    });

    it('shows no notice when no result has exactly the typed national ID', async () => {
      await typeNationalId('1710034065');
      backend
        .expectOne((request) => request.url === '/api/patients')
        .flush({
          content: [{ id: 3, nationalId: '1710034066', firstName: 'Otra', lastName: 'Persona' }],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
        });
      await fixture.whenStable();

      expect(element.querySelector('.duplicate')).toBeNull();
      await next();
      expect(selectedStep()).toBe(1);
    });

    it('reads the birth date as dd/mm/aaaa, shows the age and rejects dates that do not exist', async () => {
      await passIdentification();
      const birthDate = component()['personal'].controls.birthDate;

      await type('#patient-birth-date', '15/03/1996');
      expect(birthDate.value).toBe('1996-03-15');
      expect(element.querySelector('.age-value')?.textContent).toMatch(/\d+ años/);

      await type('#patient-birth-date', '31/02/2026');
      expect(birthDate.hasError('invalidDate')).toBe(true);
      expect(errors()).toContain('Ingrese una fecha válida en formato dd/mm/aaaa.');

      await type('#patient-birth-date', '15/03/96');
      expect(birthDate.hasError('invalidDate')).toBe(true);
    });

    it('opens the calendar of the date field', async () => {
      await passIdentification();

      (stepContent().querySelector('mat-datepicker-toggle button') as HTMLButtonElement).click();
      await fixture.whenStable();

      expect(document.querySelector('mat-calendar')).not.toBeNull();
    });

    it('validates the phone with the same message as the API', async () => {
      await passIdentification();

      await type('#patient-phone', '+593 98 765 4321');

      expect(errors()).toContain(
        'Ingrese un celular (09XXXXXXXX) o un teléfono fijo con código de provincia',
      );
    });

    it('fills the canton and parish lists from the chosen province and canton, and clears them on change', async () => {
      await passIdentification();
      const address = component()['personal'].controls.address;
      const optionsOf = async (label: string) => {
        const field = Array.from(stepContent().querySelectorAll('mat-form-field')).find((f) =>
          f.querySelector('mat-label')?.textContent?.includes(label),
        )!;
        const select = field.querySelector('mat-select') as HTMLElement;
        (field.querySelector('.mat-mdc-select-trigger') as HTMLElement).click();
        await fixture.whenStable();
        // The panel of this select (a closed panel may stay in the DOM while it fades out)
        const panel = document.getElementById(`${select.id}-panel`)!;
        const options = Array.from(panel.querySelectorAll('mat-option')).map((o) =>
          o.textContent?.trim(),
        );
        select.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        await fixture.whenStable();
        return options;
      };

      expect(await optionsOf('Provincia')).toEqual(['Guayas', 'Pichincha']);
      expect(await optionsOf('Cantón')).toEqual(['Elija primero la provincia']);

      address.controls.provinceCode.setValue('17');
      await fixture.whenStable();
      expect(await optionsOf('Cantón')).toEqual([
        'Distrito Metropolitano de Quito',
        'San Miguel de los Bancos',
      ]);
      address.controls.cantonCode.setValue('1701');
      await fixture.whenStable();
      expect(await optionsOf('Parroquia')).toEqual(['Iñaquito', 'Conocoto']);
      address.controls.parishCode.setValue('170156');

      address.controls.provinceCode.setValue('09');
      await fixture.whenStable();
      expect(address.controls.cantonCode.value).toBe('');
      expect(address.controls.parishCode.value).toBe('');
      expect(await optionsOf('Cantón')).toEqual(['Guayaquil']);
    });

    it('answers the health insurance with Sí or No', async () => {
      await passIdentification();
      fillPersonal();
      await next();
      expect(selectedStep()).toBe(2);

      const toggles = Array.from(stepContent().querySelectorAll('mat-button-toggle button'));
      expect(toggles.map((t) => t.textContent?.trim())).toEqual(['Sí', 'No']);
      (toggles[1] as HTMLButtonElement).click();
      await fixture.whenStable();

      expect(component()['admission'].controls.hasHealthInsurance.value).toBe(false);
      expect(stepContent().textContent).toContain('Datos opcionales');
    });

    it('asks for the insurance answer before moving on', async () => {
      await passIdentification();
      fillPersonal();
      await next();
      component()['admission'].patchValue({
        occupation: 'Ingeniero civil',
        educationLevel: 'Superior',
      });
      await next();

      expect(selectedStep()).toBe(2);
      expect(stepContent().textContent).toContain('Indique si tiene seguro de salud.');
    });

    it('reviews every value with "Editar" per section and registers the patient', async () => {
      await passIdentification();
      fillPersonal();
      await next();
      fillAdmission();
      await next();
      expect(selectedStep()).toBe(3);

      const review = stepContent().textContent ?? '';
      expect(review).toContain('1710034065');
      expect(review).toContain('Juan Carlos');
      expect(review).toContain('Pérez de la Torre');
      expect(review).toContain('15/03/1996');
      expect(review).toContain(
        'Av. Ilaló N12-34 y Río Pita, Conocoto, Distrito Metropolitano de Quito, Pichincha. Referencia: Frente al parque central',
      );
      expect(review).toContain('Seguro de salud');
      expect(review).toMatch(/Seguro de salud\s*No/);
      expect(stepContent().querySelectorAll('button.edit')).toHaveLength(3);

      (stepContent().querySelectorAll('button.edit')[1] as HTMLButtonElement).click();
      await fixture.whenStable();
      expect(selectedStep()).toBe(1);
      await next();
      await next();

      button('button.submit').click();
      await fixture.whenStable();
      const request = backend.expectOne('/api/patients');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual(
        expect.objectContaining({
          nationalId: '1710034065',
          firstName: 'Juan Carlos',
          lastName: 'Pérez de la Torre',
          birthDate: '1996-03-15',
          phone: '0987654321',
          hasHealthInsurance: false,
          address: {
            provinceCode: '17',
            cantonCode: '1701',
            parishCode: '170156',
            mainStreet: 'Av. Ilaló',
            streetNumber: 'N12-34',
            crossStreet: 'Río Pita',
            reference: 'Frente al parque central',
          },
        }),
      );
      const created = fakePatient(7);
      request.flush(created);
      expect(dialogRef.close).toHaveBeenCalledWith(created);
    });

    it('goes back to step 1 when the server says the national ID is taken', async () => {
      await passIdentification();
      fillPersonal();
      await next();
      fillAdmission();
      await next();

      button('button.submit').click();
      await fixture.whenStable();
      backend
        .expectOne('/api/patients')
        .flush(
          { status: 409, detail: 'Ya existe un paciente con esa cédula.', existingPatientId: 15 },
          { status: 409, statusText: 'Conflict' },
        );
      await fixture.whenStable();

      expect(selectedStep()).toBe(0);
      expect(element.querySelector('.duplicate a')?.getAttribute('href')).toBe(
        '/admin/patients/15',
      );
    });
  });

  describe('editing', () => {
    it('fills the steps, keeps the national ID read-only and warns about the data to complete', async () => {
      await create({
        patient: fakePatient(15, {
          phone: '+593 98 765 4321',
          address: legacyAddress('Calle Antigua N10-01, Quito'),
          needsCompletion: true,
        }),
      });

      expect(element.querySelector('.title')?.textContent).toContain('Editar usuario');
      expect(element.querySelector('.completion')?.textContent).toContain('datos por completar');
      expect(element.querySelector<HTMLInputElement>('#patient-national-id')?.disabled).toBe(true);
      expect(errors()).toEqual([]);
      const address = component()['personal'].controls.address;
      expect(address.controls.reference.value).toBe('Calle Antigua N10-01, Quito');
      expect(address.controls.provinceCode.value).toBe('');
      // Not linear: any step can be opened
      component()['goTo'](3);
      await fixture.whenStable();
      expect(selectedStep()).toBe(3);

      button('button.submit').click();
      await fixture.whenStable();
      // The incomplete address and the old phone send the user back to complete them
      backend.expectNone('/api/patients/15');
      expect(selectedStep()).toBe(1);
    });

    it('saves the changes with PUT', async () => {
      await create({ patient: fakePatient(15) });

      component()['goTo'](3);
      await fixture.whenStable();
      button('button.submit').click();
      await fixture.whenStable();

      const request = backend.expectOne('/api/patients/15');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body.nationalId).toBeUndefined();
      expect(request.request.body.address.parishCode).toBe('170156');
      expect(request.request.body.hasHealthInsurance).toBe(true);
      request.flush(fakePatient(15));
      expect(dialogRef.close).toHaveBeenCalled();
    });
  });
});
