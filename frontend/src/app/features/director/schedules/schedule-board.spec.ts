import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BoardPhysiotherapist } from './assignments-api';
import { fakeBoard } from './assignments-testing';
import { ScheduleBoard } from './schedule-board';

describe('ScheduleBoard (MEJ-04)', () => {
  let fixture: ComponentFixture<ScheduleBoard>;
  let element: HTMLElement;

  beforeEach(async () => {
    fixture = TestBed.createComponent(ScheduleBoard);
    fixture.componentRef.setInput('board', fakeBoard());
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  it('has one row per physiotherapist and one column per day from Monday to Saturday', () => {
    const headers = Array.from(element.querySelectorAll('thead th')).map((th) =>
      th.textContent?.trim(),
    );
    expect(headers).toEqual(['Fisioterapeuta', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb']);

    const rows = Array.from(element.querySelectorAll('tbody tr'));
    expect(rows.map((row) => row.querySelector('.name')?.textContent?.trim())).toEqual([
      'Valeria Andrade Mora',
      'Martín Cevallos Ruiz',
      'Iván Inactivo Prueba',
      'Andrés Paredes Salinas',
    ]);
    expect(rows[0].textContent).toContain('4 tratamientos activos');
    expect(rows[3].textContent).toContain('1 tratamiento activo');
  });

  it('draws each slot as a block on the clinic hours with its range', () => {
    const monday = element.querySelectorAll('tbody tr')[0].querySelectorAll('td')[0];

    const blocks = Array.from(monday.querySelectorAll<HTMLElement>('.block'));
    expect(blocks.length).toBe(2);
    // 08:00-13:00 on 07:00-20:00: starts at 1/13 of the day and lasts 5/13
    expect(parseFloat(blocks[0].style.left)).toBeCloseTo(7.69, 1);
    expect(parseFloat(blocks[0].style.width)).toBeCloseTo(38.46, 1);
    expect(Array.from(monday.querySelectorAll('li')).map((li) => li.textContent)).toEqual([
      '08:00 – 13:00',
      '14:00 – 17:00',
    ]);
    // A day without slots
    const wednesday = element.querySelectorAll('tbody tr')[0].querySelectorAll('td')[2];
    expect(wednesday.textContent).toContain('Sin atención');
  });

  it('marks the deactivated accounts', () => {
    const inactive = element.querySelectorAll('tbody tr')[2];

    expect(inactive.classList).toContain('inactive');
    expect(inactive.textContent).toContain('Cuenta inactiva');
  });

  it('asks to edit the schedule of a physiotherapist', () => {
    const edited: BoardPhysiotherapist[] = [];
    fixture.componentInstance.edit.subscribe((physiotherapist) => edited.push(physiotherapist));

    element
      .querySelector<HTMLButtonElement>('[aria-label="Editar horario de Martín Cevallos Ruiz"]')!
      .click();

    expect(edited.map((physiotherapist) => physiotherapist.staffId)).toEqual([102]);
  });

  it('also lists the same data per day, for narrow screens', () => {
    const days = Array.from(element.querySelectorAll('.day-view .day'));

    expect(days.map((day) => day.querySelector('h3')?.textContent)).toEqual([
      'Lunes',
      'Martes',
      'Miércoles',
      'Jueves',
      'Viernes',
      'Sábado',
    ]);
    expect(
      Array.from(days[0].querySelectorAll('.entry .name')).map((name) => name.textContent),
    ).toEqual(['Valeria Andrade Mora', 'Martín Cevallos Ruiz']);
    expect(days[0].textContent).toContain('07:00 – 15:00');
    expect(days[2].textContent).toContain('Nadie atiende este día.');
    expect(element.querySelectorAll('.edit-list button').length).toBe(4);
  });
});
