import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { fromHourMinute, provideEcDates } from '../../../shared/forms/ec-date-adapter';
import { fakeAvailable, fakeOperationsTreatment } from './assignments-testing';
import { ReassignDialog, ReassignDialogData } from './reassign-dialog';

describe('ReassignDialog (MEJ-04)', () => {
  let fixture: ComponentFixture<ReassignDialog>;
  let element: HTMLElement;
  let backend: HttpTestingController;
  const dialogRef = { close: vi.fn() };

  const form = () => fixture.componentInstance['form'];
  const button = (text: string) =>
    Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes(text))!;
  const optionNames = () =>
    Array.from(element.querySelectorAll('.option-name')).map((name) =>
      name.textContent?.replace(/\s+/g, ' ').trim(),
    );

  async function open(treatment = fakeOperationsTreatment(30)): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideEcDates(),
        { provide: MatDialogRef, useValue: dialogRef },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            treatment,
            openingTime: '07:00',
            closingTime: '20:00',
          } satisfies ReassignDialogData,
        },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReassignDialog);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  async function answerAvailability(day: string, from: string, to: string, found = AVAILABLE) {
    const request = backend.expectOne(
      (req) => req.url === '/api/operations/availability' && req.params.get('day') === day,
    );
    expect(request.request.params.get('from')).toBe(from);
    expect(request.request.params.get('to')).toBe(to);
    request.flush(found);
    await fixture.whenStable();
  }

  /** As the API orders them: by load, then by last names. */
  const AVAILABLE = [
    fakeAvailable(4, 'Andrés Paredes Salinas', 1),
    fakeAvailable(2, 'Martín Cevallos Ruiz', 2),
    fakeAvailable(1, 'Valeria Andrade Mora', 4),
  ];

  beforeEach(() => dialogRef.close.mockReset());
  afterEach(() => backend.verify());

  it('searches the first day of the current schedule and lists the available ones by load', async () => {
    await open();
    await answerAvailability('MONDAY', '08:00', '10:00');

    expect(element.querySelector('.subtitle')?.textContent).toContain(
      'Rosa Elena Ortiz Vega · Responsable vigente: Valeria Andrade Mora',
    );
    expect(optionNames()).toEqual([
      'Andrés Paredes Salinas',
      'Martín Cevallos Ruiz',
      'Valeria Andrade Mora (responsable vigente)',
    ]);
    expect(element.querySelector('.results-title')?.textContent).toContain(
      'Disponibles (3), de menor a mayor carga',
    );
    expect(element.querySelectorAll('.option-meta')[0].textContent).toContain(
      '1 tratamiento activo',
    );
    // The current responsible cannot be chosen again
    const radios = Array.from(element.querySelectorAll<HTMLInputElement>('input[type="radio"]'));
    expect(radios.map((radio) => radio.disabled)).toEqual([false, false, true]);
  });

  it('searches again with another day and range', async () => {
    await open();
    await answerAvailability('MONDAY', '08:00', '10:00');

    form().patchValue({
      day: 'SATURDAY',
      from: fromHourMinute('09:00'),
      to: fromHourMinute('11:30'),
    });
    button('Buscar disponibles').click();
    await answerAvailability('SATURDAY', '09:00', '11:30', []);

    expect(element.querySelector('.notice')?.textContent).toContain(
      'Nadie está disponible el sábado en ese horario. Pruebe otro día o rango.',
    );
  });

  it('does not search an inverted range', async () => {
    await open();
    await answerAvailability('MONDAY', '08:00', '10:00');

    form().patchValue({ from: fromHourMinute('12:00'), to: fromHourMinute('11:00') });
    await fixture.whenStable();

    expect(element.querySelector('.range-problem')?.textContent).toContain(
      'La hora inicial debe ser anterior a la hora final.',
    );
    expect(button('Buscar disponibles').disabled).toBe(true);
    backend.expectNone('/api/operations/availability');
  });

  it('requires a choice, then reassigns and closes with the updated treatment', async () => {
    await open(
      fakeOperationsTreatment(31, {
        responsible: { id: 3, fullName: 'Iván Inactivo Prueba' },
        responsibleInactive: true,
        scheduleDays: [],
      }),
    );
    await answerAvailability('MONDAY', '08:00', '10:00');
    expect(element.querySelector('.subtitle')?.textContent).toContain('(cuenta inactiva)');

    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(element.querySelector('.error')?.textContent).toContain(
      'Elija un fisioterapeuta disponible.',
    );

    form().controls.physiotherapistId.setValue(4);
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const request = backend.expectOne('/api/operations/treatments/31/responsible');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ physiotherapistId: 4 });
    const updated = fakeOperationsTreatment(31, {
      responsible: { id: 4, fullName: 'Andrés Paredes Salinas' },
    });
    request.flush(updated);

    expect(dialogRef.close).toHaveBeenCalledWith(updated);
  });

  it('shows the message of the API when the reassignment fails', async () => {
    await open();
    await answerAvailability('MONDAY', '08:00', '10:00');

    form().controls.physiotherapistId.setValue(2);
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    backend
      .expectOne('/api/operations/treatments/30/responsible')
      .flush(
        { detail: 'El nuevo responsable debe ser un fisioterapeuta activo.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await fixture.whenStable();

    expect(element.querySelector('.error')?.textContent).toContain(
      'El nuevo responsable debe ser un fisioterapeuta activo.',
    );
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
