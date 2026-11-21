import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Page } from '../../../core/http/page';
import { ReportTreatment } from './report-api';
import { ReportTreatmentList } from './report-treatment-list';
import { fakeReportTreatment, fakeTreatmentPage } from './report-testing';

const TREATMENTS: ReportTreatment[] = [
  fakeReportTreatment(),
  fakeReportTreatment({
    id: 20,
    patientName: 'Luis Alberto Quishpe Tipán',
    diagnosis: { code: 'S93.4', description: 'Esguince y torcedura del tobillo' },
    responsibleName: 'Lcdo. Mateo Ríos',
    status: 'COMPLETED',
    startDate: '2026-08-12',
  }),
  fakeReportTreatment({
    id: 10,
    patientName: 'Daniela Sofía Zambrano Loor',
    responsibleName: null,
    status: 'ABANDONED',
    startDate: '2026-06-01',
  }),
];

describe('ReportTreatmentList', () => {
  let fixture: ComponentFixture<ReportTreatmentList>;
  let element: HTMLElement;
  let pages: number[];

  const clean = (node: Element | null | undefined) =>
    node?.textContent?.replace(/\s+/g, ' ').trim();
  const rows = () => Array.from(element.querySelectorAll<HTMLElement>('tbody tr'));
  const cell = (row: number, column: number) => rows()[row].querySelectorAll('td')[column];

  function render(page: Page<ReportTreatment>, status = 'Todos', physiotherapist = 'Todos'): void {
    fixture = TestBed.createComponent(ReportTreatmentList);
    fixture.componentRef.setInput('page', page);
    fixture.componentRef.setInput('pageSize', 10);
    fixture.componentRef.setInput('statusFilter', status);
    fixture.componentRef.setInput('physiotherapistFilter', physiotherapist);
    pages = [];
    fixture.componentInstance.pageChange.subscribe((page) => pages.push(page));
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  it('has the title "Tratamientos (N)" with the total and the filters applied', () => {
    render(
      fakeTreatmentPage(TREATMENTS, { totalElements: 23, totalPages: 3 }),
      'Activo',
      'Lcdo. Mateo Ríos',
    );

    expect(clean(element.querySelector('.title'))).toBe('Tratamientos (23)');
    expect(clean(element.querySelector('.subtitle'))).toBe(
      'Filtrado por estado: Activo • Especialista: Lcdo. Mateo Ríos',
    );
    expect(clean(element.querySelector('caption'))).toBe('Tratamientos del reporte');
    // Without a range there is no period nor note
    expect(element.querySelector('.note')).toBeNull();
  });

  it('with a range, shows the period and that the status is the current one (HU-15)', () => {
    render(fakeTreatmentPage(TREATMENTS));
    fixture.componentRef.setInput('periodFilter', 'Del 01/10/2026 al 31/10/2026');
    fixture.detectChanges();

    expect(clean(element.querySelector('.subtitle'))).toBe(
      'Filtrado por estado: Todos • Especialista: Todos • Periodo: Del 01/10/2026 al 31/10/2026',
    );
    expect(clean(element.querySelector('.note'))).toBe(
      'El estado corresponde al estado actual de cada tratamiento.',
    );
  });

  it('shows the patient, the diagnosis, the responsible, the status and the start date', () => {
    render(fakeTreatmentPage(TREATMENTS));

    expect(clean(cell(0, 0).querySelector('.patient'))).toBe('Rosa Elena Ortiz Vega');
    expect(clean(cell(0, 0).querySelectorAll('.meta')[0])).toBe('M54.5 · Lumbago no especificado');
    expect(clean(cell(0, 0).querySelector('.responsible span'))).toBe('Lcda. Valeria Andrade');
    expect(clean(cell(0, 1).querySelector('app-status-badge'))).toBe('Activo');
    expect(clean(cell(0, 1).querySelector('.when'))).toBe('Inicio: 05/10/2026');
    expect(rows().map((row) => clean(row.querySelector('app-status-badge')))).toEqual([
      'Activo',
      'Finalizado',
      'Abandonado',
    ]);
    expect(clean(cell(2, 0).querySelector('.responsible span'))).toBe('Sin responsable');
  });

  it('never shows a national ID nor other personal data of the patient', () => {
    render(fakeTreatmentPage(TREATMENTS));

    expect(element.textContent).not.toMatch(/Céd|C\.I\.|cédula|\d{10}/i);
    expect(Array.from(element.querySelectorAll('thead th')).map(clean)).toEqual([
      'Paciente y diagnóstico',
      'Estado',
    ]);
  });

  it('pages in the server: it shows the page received and asks for the next one', () => {
    render(fakeTreatmentPage(TREATMENTS, { page: 1, totalElements: 23, totalPages: 3 }));

    expect(clean(element.querySelector('.range'))).toBe('Mostrando 11–20 de 23 tratamientos');
    element.querySelector<HTMLButtonElement>('button[aria-label="Página siguiente"]')!.click();

    expect(pages).toEqual([2]);
  });

  it('shows a message when no treatment matches the filters', () => {
    render(fakeTreatmentPage([]));

    expect(clean(element.querySelector('.title'))).toBe('Tratamientos (0)');
    expect(clean(element.querySelector('td.empty'))).toBe(
      'No hay tratamientos que coincidan con los filtros.',
    );
  });

  it('has no control to edit anything: only the pager', () => {
    render(fakeTreatmentPage(TREATMENTS, { totalElements: 23, totalPages: 3 }));

    expect(element.querySelectorAll('a, input, select, textarea')).toHaveLength(0);
    expect(
      Array.from(element.querySelectorAll('button')).map((button) =>
        button.getAttribute('aria-label'),
      ),
    ).toEqual(['Página anterior', 'Página siguiente']);
  });
});
