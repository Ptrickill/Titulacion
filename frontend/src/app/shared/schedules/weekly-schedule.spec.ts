import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ScheduleSlot } from './schedule-api';
import { slot } from './schedule-testing';
import { WeeklySchedule } from './weekly-schedule';

describe('WeeklySchedule (MEJ-04)', () => {
  let fixture: ComponentFixture<WeeklySchedule>;

  async function render(slots: ScheduleSlot[]): Promise<HTMLElement> {
    fixture = TestBed.createComponent(WeeklySchedule);
    fixture.componentRef.setInput('slots', slots);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows every day from Monday to Saturday with its slots in order', async () => {
    const element = await render([
      slot('MONDAY', '14:00', '17:00'),
      slot('MONDAY', '08:00', '13:00'),
      slot('THURSDAY', '13:00', '19:00'),
    ]);

    const days = Array.from(element.querySelectorAll('.day'));
    expect(days.map((day) => day.querySelector('dt')?.textContent?.trim())).toEqual([
      'Lunes',
      'Martes',
      'Miércoles',
      'Jueves',
      'Viernes',
      'Sábado',
    ]);
    expect(Array.from(days[0].querySelectorAll('.slot')).map((s) => s.textContent?.trim())).toEqual(
      ['08:00 – 13:00', '14:00 – 17:00'],
    );
    expect(days[1].textContent).toContain('Sin atención');
    expect(days[1].classList).toContain('off');
    expect(element.querySelector('.total')?.textContent).toContain('Total semanal: 14 h');
  });

  it('says when there are no slots yet', async () => {
    const element = await render([]);

    expect(element.querySelectorAll('.slot').length).toBe(0);
    expect(element.querySelector('.total')?.textContent).toContain(
      'Aún no tiene franjas registradas.',
    );
  });
});
