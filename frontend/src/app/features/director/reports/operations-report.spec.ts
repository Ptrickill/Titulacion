import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { provideRouter } from '@angular/router';
import { logInForTest } from '../../../core/auth/auth-testing';
import { OperationsReport } from './operations-report';
import { ReportSummary, ReportTreatment } from './report-api';
import {
  FAKE_PHYSIOTHERAPISTS,
  fakeReportTreatment,
  fakeSummary,
  fakeTreatmentPage,
} from './report-testing';

const SUMMARY = '/api/reports/summary';
const TREATMENTS = '/api/reports/treatments';

describe('OperationsReport', () => {
  let fixture: ComponentFixture<OperationsReport>;
  let backend: HttpTestingController;
  let loader: HarnessLoader;
  let element: HTMLElement;

  const clean = (node: Element | null | undefined) =>
    node?.textContent?.replace(/\s+/g, ' ').trim();
  const text = (selector: string) => clean(element.querySelector(selector));
  const cards = () => Array.from(element.querySelectorAll('app-stat-card'));
  const pending = (url: string) => backend.match((request) => request.url === url);

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** Opens the page; its three requests stay pending. */
  function open(): void {
    logInForTest('OPERATIONS_DIRECTOR');
    fixture = TestBed.createComponent(OperationsReport);
    loader = TestbedHarnessEnvironment.loader(fixture);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  function one(url: string): TestRequest {
    const requests = pending(url);
    expect(requests, url).toHaveLength(1);
    return requests[0];
  }

  /** The two pending requests of the report; taking them removes them from the queue. */
  function reportRequests(): { summary: TestRequest; treatments: TestRequest } {
    return { summary: one(SUMMARY), treatments: one(TREATMENTS) };
  }

  async function reply(
    requests: { summary: TestRequest; treatments: TestRequest },
    summary: ReportSummary = fakeSummary(),
    treatments: ReportTreatment[] = [fakeReportTreatment()],
  ): Promise<void> {
    requests.summary.flush(summary);
    requests.treatments.flush(fakeTreatmentPage(treatments));
    await settle();
  }

  async function answer(
    summary: ReportSummary = fakeSummary(),
    treatments: ReportTreatment[] = [fakeReportTreatment()],
  ): Promise<void> {
    await reply(reportRequests(), summary, treatments);
  }

  /** Opens the page and answers the filter options, the summary and the first page. */
  async function openAndAnswer(): Promise<void> {
    open();
    backend.expectOne('/api/reports/physiotherapists').flush(FAKE_PHYSIOTHERAPISTS);
    await answer();
  }

  /** The two selects, in the order of the form. */
  const FILTERS = ['Estado del tratamiento', 'Fisioterapeuta responsable'];

  async function choose(filter: string, option: string): Promise<void> {
    const select = (await loader.getAllHarnesses(MatSelectHarness))[FILTERS.indexOf(filter)];
    await select.open();
    await select.clickOptions({ text: option });
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('has the title of the prototype, the read-only notice and no export button', async () => {
    await openAndAnswer();

    expect(text('app-page-header .title')).toBe('Reportes de Dirección Operativa');
    expect(text('app-page-header .subtitle')).toBe(
      'Métricas de tratamientos, adherencia clínica y distribución por fisioterapeuta',
    );
    expect(text('.notice span')).toBe(
      'Modo de Dirección Operativa: acceso de solo consulta para revisar tratamientos ' +
        'activos, altas, abandonos y productividad de sesiones por especialista.',
    );
    expect(element.querySelector('.notice')?.getAttribute('role')).toBe('note');
    // Exporting the report is HU-15
    expect(element.textContent).not.toMatch(/Exportar/);
  });

  it('asks for the summary and the first page without filters', async () => {
    open();
    backend.expectOne('/api/reports/physiotherapists').flush(FAKE_PHYSIOTHERAPISTS);

    const requests = reportRequests();
    expect(requests.summary.request.params.keys()).toEqual([]);
    const treatments = requests.treatments.request.params;
    expect(treatments.keys().sort()).toEqual(['page', 'size']);
    expect(treatments.get('page')).toBe('0');
    expect(treatments.get('size')).toBe('5');
    await reply(requests);
  });

  it('starts with "Todos los estados" and "Todos los fisioterapeutas" chosen', async () => {
    await openAndAnswer();

    const [status, physiotherapist] = await loader.getAllHarnesses(MatSelectHarness);
    expect(await status.getValueText()).toBe('Todos los estados');
    expect(await physiotherapist.getValueText()).toBe('Todos los fisioterapeutas');
    await physiotherapist.open();
    expect(
      await Promise.all((await physiotherapist.getOptions()).map((option) => option.getText())),
    ).toEqual(['Todos los fisioterapeutas', 'Lcda. Valeria Andrade', 'Lcdo. Mateo Ríos']);
  });

  it('shows the five indicators, with the sessions per status in the footer of the sessions', async () => {
    await openAndAnswer();

    expect(cards().map((card) => clean(card.querySelector('.label')))).toEqual([
      'Tratamientos activos',
      'Tratamientos finalizados',
      'Abandonos',
      'Sesiones registradas',
      'Sesiones por fisioterapeuta',
    ]);
    expect(cards().map((card) => clean(card.querySelector('.value')))).toEqual([
      '5',
      '1',
      '1',
      '28',
      '5,6',
    ]);
    expect(clean(cards()[3].querySelector('.hint'))).toBe(
      '24 realizadas · 3 reprogramadas · 1 no asistió',
    );
    expect(clean(cards()[4].querySelector('.hint'))).toBe('Promedio por profesional');
  });

  it('says "realizada" and "reprogramada" for one session', async () => {
    open();
    backend.expectOne('/api/reports/physiotherapists').flush(FAKE_PHYSIOTHERAPISTS);
    await answer(fakeSummary({ sessions: { total: 3, completed: 1, rescheduled: 1, noShow: 1 } }));

    expect(clean(cards()[3].querySelector('.hint'))).toBe(
      '1 realizada · 1 reprogramada · 1 no asistió',
    );
  });

  it('shows the sessions of each physiotherapist and the list of treatments', async () => {
    await openAndAnswer();

    expect(element.querySelectorAll('app-sessions-by-physiotherapist-card li')).toHaveLength(5);
    expect(text('app-report-treatment-list .title')).toBe('Tratamientos (1)');
    expect(text('app-report-treatment-list .subtitle')).toBe(
      'Filtrado por estado: Todos • Especialista: Todos',
    );
    expect(text('app-report-treatment-list .patient')).toBe('Rosa Elena Ortiz Vega');
  });

  it('reloads the summary and the first page with the status chosen', async () => {
    await openAndAnswer();

    await choose('Estado del tratamiento', 'Finalizado');

    const requests = reportRequests();
    expect(requests.summary.request.params.get('status')).toBe('COMPLETED');
    expect(requests.treatments.request.params.get('status')).toBe('COMPLETED');
    expect(requests.treatments.request.params.get('page')).toBe('0');
    await reply(requests);
    expect(text('app-report-treatment-list .subtitle')).toBe(
      'Filtrado por estado: Finalizado • Especialista: Todos',
    );
  });

  it('reloads the summary and the first page with the physiotherapist chosen', async () => {
    await openAndAnswer();

    await choose('Fisioterapeuta responsable', 'Lcdo. Mateo Ríos');

    const requests = reportRequests();
    expect(requests.summary.request.params.get('physiotherapistId')).toBe('4');
    expect(requests.summary.request.params.has('status')).toBe(false);
    expect(requests.treatments.request.params.get('physiotherapistId')).toBe('4');
    await reply(requests);
    expect(text('app-report-treatment-list .subtitle')).toBe(
      'Filtrado por estado: Todos • Especialista: Lcdo. Mateo Ríos',
    );
  });

  it('combines both filters and goes back to all with "Todos"', async () => {
    await openAndAnswer();

    await choose('Estado del tratamiento', 'Activo');
    await answer();
    await choose('Fisioterapeuta responsable', 'Lcda. Valeria Andrade');

    const combined = reportRequests();
    expect(combined.summary.request.params.get('status')).toBe('ACTIVE');
    expect(combined.summary.request.params.get('physiotherapistId')).toBe('3');
    expect(combined.treatments.request.params.get('status')).toBe('ACTIVE');
    expect(combined.treatments.request.params.get('physiotherapistId')).toBe('3');
    await reply(combined);

    await choose('Estado del tratamiento', 'Todos los estados');
    const all = reportRequests();
    expect(all.summary.request.params.keys()).toEqual(['physiotherapistId']);
    await reply(all);
    expect(text('app-report-treatment-list .subtitle')).toBe(
      'Filtrado por estado: Todos • Especialista: Lcda. Valeria Andrade',
    );
  });

  it('only asks for the list again when the page changes, and a filter goes back to the first page', async () => {
    open();
    backend.expectOne('/api/reports/physiotherapists').flush(FAKE_PHYSIOTHERAPISTS);
    one(SUMMARY).flush(fakeSummary());
    const five = Array.from({ length: 5 }, (_, index) => fakeReportTreatment({ id: index + 1 }));
    one(TREATMENTS).flush(fakeTreatmentPage(five, { size: 5, totalElements: 12, totalPages: 3 }));
    await settle();

    element.querySelector<HTMLButtonElement>('button[aria-label="Página siguiente"]')!.click();
    await settle();

    expect(pending(SUMMARY)).toHaveLength(0);
    const second = one(TREATMENTS);
    expect(second.request.params.get('page')).toBe('1');
    second.flush(fakeTreatmentPage(five, { page: 1, size: 5, totalElements: 12, totalPages: 3 }));
    await settle();
    expect(text('.range')).toBe('Mostrando 6–10 de 12 tratamientos');

    await choose('Estado del tratamiento', 'Abandonado');
    const requests = reportRequests();
    expect(requests.treatments.request.params.get('page')).toBe('0');
    await reply(requests);
  });

  it('keeps working without the list of physiotherapists', async () => {
    open();
    backend
      .expectOne('/api/reports/physiotherapists')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await answer();

    expect(cards()).toHaveLength(5);
    expect(element.querySelector('app-empty-state')).toBeNull();
  });

  it('shows the error with "Reintentar", which asks again for the same filters', async () => {
    open();
    backend.expectOne('/api/reports/physiotherapists').flush(FAKE_PHYSIOTHERAPISTS);
    one(SUMMARY).flush(
      { title: 'Error interno', detail: 'Ocurrió un error inesperado.', status: 500 },
      { status: 500, statusText: 'Server Error' },
    );
    one(TREATMENTS).flush(fakeTreatmentPage([fakeReportTreatment()]));
    await settle();

    expect(text('app-empty-state .title')).toBe('No se pudo cargar el reporte');
    expect(text('app-empty-state .message')).toBe('Ocurrió un error inesperado.');
    expect(cards()).toHaveLength(0);
    expect(element.querySelector('app-report-treatment-list')).toBeNull();

    element.querySelector<HTMLButtonElement>('app-empty-state button')!.click();
    await settle();
    await answer();

    expect(element.querySelector('app-empty-state')).toBeNull();
    expect(cards()).toHaveLength(5);
  });

  it('shows a progress bar while it loads', async () => {
    open();
    backend.expectOne('/api/reports/physiotherapists').flush(FAKE_PHYSIOTHERAPISTS);
    await settle();
    expect(element.querySelector('mat-progress-bar')).not.toBeNull();

    await answer();
    expect(element.querySelector('mat-progress-bar')).toBeNull();
  });

  it('has no control to edit anything: only the two filters and the pager', async () => {
    open();
    backend.expectOne('/api/reports/physiotherapists').flush(FAKE_PHYSIOTHERAPISTS);
    one(SUMMARY).flush(fakeSummary());
    one(TREATMENTS).flush(
      fakeTreatmentPage([fakeReportTreatment()], { totalElements: 25, totalPages: 3 }),
    );
    await settle();

    expect(element.querySelectorAll('a, input, textarea')).toHaveLength(0);
    expect(element.querySelectorAll('mat-select')).toHaveLength(2);
    expect(
      Array.from(element.querySelectorAll('button')).map((button) =>
        button.getAttribute('aria-label'),
      ),
    ).toEqual(['Página anterior', 'Página siguiente']);
    expect(element.textContent).not.toMatch(/Editar|Guardar|Eliminar|Registrar|Notificaci/);
  });
});
