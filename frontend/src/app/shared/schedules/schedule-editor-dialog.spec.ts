import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { fromHourMinute, provideEcDates } from '../forms/ec-date-adapter';
import { ScheduleEditorDialog, ScheduleEditorDialogData } from './schedule-editor-dialog';
import { fakeSchedule, slot } from './schedule-testing';

describe('ScheduleEditorDialog (MEJ-04)', () => {
  let fixture: ComponentFixture<ScheduleEditorDialog>;
  let element: HTMLElement;
  let backend: HttpTestingController;
  const dialogRef = { close: vi.fn() };

  const slots = () => fixture.componentInstance['slots'];
  const rows = () => Array.from(element.querySelectorAll('fieldset.slot'));
  const button = (text: string) =>
    Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes(text))!;

  async function open(
    schedule = fakeSchedule(4, {
      slots: [slot('MONDAY', '08:00', '12:00'), slot('TUESDAY', '14:00', '18:00')],
    }),
  ): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideEcDates(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { schedule } satisfies ScheduleEditorDialogData },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ScheduleEditorDialog);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  async function save(): Promise<void> {
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  beforeEach(() => dialogRef.close.mockReset());
  afterEach(() => backend.verify());

  it('shows each slot with time pickers and the clinic hours', async () => {
    await open();

    expect(element.querySelector('.subtitle')?.textContent).toContain(
      'Valeria Andrade Mora · Jornada de la clínica: 07:00 a 20:00',
    );
    expect(rows().length).toBe(2);
    expect(element.querySelectorAll('mat-timepicker-toggle').length).toBe(4);
    const inputs = Array.from(element.querySelectorAll<HTMLInputElement>('.mat-timepicker-input'));
    expect(inputs.map((input) => input.value)).toEqual(['08:00', '12:00', '14:00', '18:00']);
  });

  it('marks an overlapping slot as soon as it overlaps, and does not save', async () => {
    await open();

    slots()
      .at(1)
      .setValue({
        day: 'MONDAY',
        start: fromHourMinute('11:00'),
        end: fromHourMinute('13:00'),
      });
    await fixture.whenStable();

    const problems = Array.from(element.querySelectorAll('.problem span')).map((p) =>
      p.textContent?.trim(),
    );
    expect(problems).toEqual([
      'Se superpone con la franja de 11:00 – 13:00 del lunes.',
      'Se superpone con la franja de 08:00 – 12:00 del lunes.',
    ]);
    expect(rows()[0].classList).toContain('invalid');

    await save();
    expect(element.querySelector('.error')?.textContent).toContain(
      'Revise las franjas marcadas antes de guardar.',
    );
    backend.expectNone('/api/staff/4/schedule');
  });

  it('adds and removes slots, and saves the week as HH:mm', async () => {
    await open();

    button('Agregar franja').click();
    await fixture.whenStable();
    expect(rows().length).toBe(3);
    // The new row goes on the first day without slots, in the morning
    expect(slots().at(2).getRawValue().day).toBe('WEDNESDAY');

    element.querySelector<HTMLButtonElement>('[aria-label="Quitar franja 1"]')!.click();
    await fixture.whenStable();
    expect(rows().length).toBe(2);

    await save();
    const request = backend.expectOne('/api/staff/4/schedule');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      slots: [slot('TUESDAY', '14:00', '18:00'), slot('WEDNESDAY', '08:00', '12:00')],
    });
    const saved = fakeSchedule(4, { slots: request.request.body.slots });
    request.flush(saved);
    expect(dialogRef.close).toHaveBeenCalledWith(saved);
  });

  it('removing every slot leaves the physiotherapist without a schedule', async () => {
    await open(fakeSchedule(4, { slots: [slot('MONDAY', '08:00', '12:00')] }));

    element.querySelector<HTMLButtonElement>('[aria-label="Quitar franja 1"]')!.click();
    await fixture.whenStable();
    expect(element.querySelector('.empty')?.textContent).toContain(
      'Sin franjas: el fisioterapeuta quedará sin horario.',
    );

    await save();
    backend.expectOne('/api/staff/4/schedule').flush(fakeSchedule(4, { slots: [] }));
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('shows the message of the API and stays open', async () => {
    await open();

    await save();
    backend
      .expectOne('/api/staff/4/schedule')
      .flush(
        { detail: 'Las franjas del lunes se superponen: 08:00–12:00 y 11:00–13:00.' },
        { status: 400, statusText: 'Bad Request' },
      );
    await fixture.whenStable();

    expect(element.querySelector('.error')?.textContent).toContain(
      'Las franjas del lunes se superponen',
    );
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
