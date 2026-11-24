import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssignmentList } from './assignment-list';
import { OperationsTreatment } from './assignments-api';
import { fakeOperationsPage, fakeOperationsTreatment } from './assignments-testing';

describe('AssignmentList (MEJ-04)', () => {
  let fixture: ComponentFixture<AssignmentList>;
  let element: HTMLElement;

  async function render(content: OperationsTreatment[], onlyInactive = false): Promise<void> {
    fixture = TestBed.createComponent(AssignmentList);
    fixture.componentRef.setInput('page', fakeOperationsPage(content, 12));
    fixture.componentRef.setInput('pageSize', 10);
    fixture.componentRef.setInput('onlyInactiveResponsible', onlyInactive);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  it('shows the patient, the responsible, the start date and the days, without clinical data', async () => {
    await render([
      fakeOperationsTreatment(30),
      fakeOperationsTreatment(31, {
        patientName: 'Daniela Sofía Zambrano Loor',
        responsible: { id: 3, fullName: 'Iván Inactivo Prueba' },
        responsibleInactive: true,
        scheduleDays: [],
      }),
    ]);

    expect(element.querySelector('.title')?.textContent).toContain('Tratamientos activos (12)');
    const rows = Array.from(element.querySelectorAll('tbody tr'));
    expect(rows[0].textContent).toContain('Rosa Elena Ortiz Vega');
    expect(rows[0].textContent).toContain('Inicio: 08/09/2026');
    expect(rows[0].textContent).toContain('Valeria Andrade Mora');
    expect(rows[0].querySelector('.days')?.getAttribute('aria-label')).toBe('Lunes, Martes');
    expect(rows[0].textContent).not.toContain('Responsable inactivo');
    expect(rows[1].textContent).toContain('Responsable inactivo');
    expect(rows[1].textContent).toContain('Sin horario');
    expect(element.querySelector('tbody')?.textContent).not.toMatch(/diagn|CIE|EVA|M54/i);
  });

  it('asks to reassign a treatment', async () => {
    await render([fakeOperationsTreatment(30)]);
    const asked: number[] = [];
    fixture.componentInstance.reassign.subscribe((treatment) => asked.push(treatment.id));

    element
      .querySelector<HTMLButtonElement>(
        '[aria-label="Reasignar el tratamiento de Rosa Elena Ortiz Vega"]',
      )!
      .click();

    expect(asked).toEqual([30]);
  });

  it('switches to the treatments with an inactive responsible', async () => {
    await render([fakeOperationsTreatment(30)]);
    const filters: boolean[] = [];
    fixture.componentInstance.filterChange.subscribe((value) => filters.push(value));

    const toggle = Array.from(
      element.querySelectorAll<HTMLButtonElement>('mat-button-toggle button'),
    ).find((button) => button.textContent?.includes('Responsable inactivo'))!;
    toggle.click();

    expect(filters).toEqual([true]);
  });

  it('says when no treatment has an inactive responsible', async () => {
    fixture = TestBed.createComponent(AssignmentList);
    fixture.componentRef.setInput('page', fakeOperationsPage([]));
    fixture.componentRef.setInput('pageSize', 10);
    fixture.componentRef.setInput('onlyInactiveResponsible', true);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();

    expect(element.textContent).toContain(
      'Ningún tratamiento activo tiene un responsable inactivo.',
    );
  });
});
