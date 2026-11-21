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
import { FileDownload } from '../../../core/http/file-download';
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

  const exportButton = () => element.querySelector<HTMLButtonElement>('app-page-header .export')!;
  const dateInput = (name: 'from' | 'to') =>
    element.querySelector<HTMLInputElement>(`input[formControlName="${name}"]`)!;

  /** Types a date (yyyy-MM-dd) in "Desde" or "Hasta". */
  async function type(name: 'from' | 'to', date: string): Promise<void> {
    const input = dateInput(name);
    input.value = date;
    input.dispatchEvent(new Event('input'));
    await settle();
  }

  let download: { save: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    download = { save: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: FileDownload, useValue: download },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('has the title of the prototype, the read-only notice and the export button', async () => {
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
    expect(text('app-page-header .export')).toBe('download Exportar informe operativo');
    expect(exportButton().disabled).toBe(false);
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

  it('has no control to edit anything: only the filters, the export and the pager', async () => {
    open();
    backend.expectOne('/api/reports/physiotherapists').flush(FAKE_PHYSIOTHERAPISTS);
    one(SUMMARY).flush(fakeSummary());
    one(TREATMENTS).flush(
      fakeTreatmentPage([fakeReportTreatment()], { totalElements: 25, totalPages: 3 }),
    );
    await settle();

    expect(element.querySelectorAll('a, textarea')).toHaveLength(0);
    expect(element.querySelectorAll('mat-select')).toHaveLength(2);
    expect(Array.from(element.querySelectorAll('input')).map((input) => input.type)).toEqual([
      'date',
      'date',
    ]);
    expect(
      Array.from(element.querySelectorAll('button')).map(
        (button) => button.getAttribute('aria-label') ?? clean(button),
      ),
    ).toEqual(['download Exportar informe operativo', 'Página anterior', 'Página siguiente']);
    expect(element.textContent).not.toMatch(/Editar|Guardar|Eliminar|Registrar|Notificaci/);
  });

  // ----- date range (HU-15) -----

  describe('date range', () => {
    it('asks for the summary and the first page of the range, both days included', async () => {
      await openAndAnswer();

      await type('from', '2026-10-01');
      await answer();
      await type('to', '2026-10-31');

      const requests = reportRequests();
      expect(requests.summary.request.params.get('from')).toBe('2026-10-01');
      expect(requests.summary.request.params.get('to')).toBe('2026-10-31');
      expect(requests.treatments.request.params.get('from')).toBe('2026-10-01');
      expect(requests.treatments.request.params.get('to')).toBe('2026-10-31');
      expect(requests.treatments.request.params.get('page')).toBe('0');
      await reply(requests);
    });

    it('shows the period and the note of the current status in the list only with a range', async () => {
      await openAndAnswer();
      expect(text('app-report-treatment-list .note')).toBeUndefined();

      await type('from', '2026-10-01');
      await answer();
      expect(text('app-report-treatment-list .subtitle')).toBe(
        'Filtrado por estado: Todos • Especialista: Todos • Periodo: Desde el 01/10/2026',
      );
      expect(text('app-report-treatment-list .note')).toBe(
        'El estado corresponde al estado actual de cada tratamiento.',
      );

      await type('to', '2026-10-31');
      await answer();
      expect(text('app-report-treatment-list .subtitle')).toContain(
        'Periodo: Del 01/10/2026 al 31/10/2026',
      );
    });

    it('combines the range with the status and the physiotherapist', async () => {
      await openAndAnswer();

      await choose('Estado del tratamiento', 'Activo');
      await answer();
      await choose('Fisioterapeuta responsable', 'Lcdo. Mateo Ríos');
      await answer();
      await type('to', '2026-10-31');

      const { summary, treatments } = reportRequests();
      expect(summary.request.params.keys().sort()).toEqual(['physiotherapistId', 'status', 'to']);
      expect(treatments.request.params.get('status')).toBe('ACTIVE');
      expect(treatments.request.params.get('physiotherapistId')).toBe('4');
      expect(treatments.request.params.get('to')).toBe('2026-10-31');
      await reply({ summary, treatments });
    });

    it('does not search with "Desde" after "Hasta" and says why until it is fixed', async () => {
      await openAndAnswer();
      await type('to', '2026-10-01');
      await answer();

      await type('from', '2026-10-31');

      expect(pending(SUMMARY)).toHaveLength(0);
      expect(pending(TREATMENTS)).toHaveLength(0);
      expect(text('.range-error')).toBe(
        'La fecha inicial no puede ser posterior a la fecha final.',
      );
      expect(element.querySelector('.range-error')?.getAttribute('role')).toBe('alert');
      expect(cards()).toHaveLength(0);
      expect(exportButton().disabled).toBe(true);

      await type('from', '2026-09-01');
      const requests = reportRequests();
      expect(requests.summary.request.params.get('from')).toBe('2026-09-01');
      await reply(requests);
      expect(element.querySelector('.range-error')).toBeNull();
      expect(exportButton().disabled).toBe(false);
    });

    it('clears a date with its button', async () => {
      await openAndAnswer();
      expect(element.querySelector('button[aria-label="Borrar la fecha inicial"]')).toBeNull();
      await type('from', '2026-10-01');
      await answer();

      element
        .querySelector<HTMLButtonElement>('button[aria-label="Borrar la fecha inicial"]')!
        .click();
      await settle();

      const requests = reportRequests();
      expect(requests.summary.request.params.has('from')).toBe(false);
      await reply(requests);
      expect(dateInput('from').value).toBe('');
      expect(text('app-report-treatment-list .note')).toBeUndefined();
    });
  });

  // ----- export (HU-15) -----

  describe('export', () => {
    const PDF = '/api/reports/summary.pdf';

    it('downloads the PDF with the filters the page shows', async () => {
      await openAndAnswer();
      await choose('Estado del tratamiento', 'Finalizado');
      await answer();
      await type('from', '2026-10-01');
      await answer();

      exportButton().click();
      await settle();

      const request = backend.expectOne((candidate) => candidate.url === PDF);
      expect(request.request.responseType).toBe('blob');
      expect(request.request.params.keys().sort()).toEqual(['from', 'status']);
      expect(request.request.params.get('status')).toBe('COMPLETED');
      expect(request.request.params.get('from')).toBe('2026-10-01');
      const pdf = new Blob(['%PDF-1.4'], { type: 'application/pdf' });
      request.flush(pdf);
      await settle();

      expect(download.save).toHaveBeenCalledOnce();
      expect(download.save).toHaveBeenCalledWith(
        pdf,
        expect.stringMatching(/^informe-operativo-\d{4}-\d{2}-\d{2}\.pdf$/),
      );
      expect(element.querySelector('.export-error')).toBeNull();
    });

    it('is disabled and says "Exportando…" until the PDF arrives', async () => {
      await openAndAnswer();

      exportButton().click();
      await settle();

      expect(exportButton().disabled).toBe(true);
      expect(clean(exportButton())).toBe('download Exportando…');
      // A second click while exporting asks for nothing
      exportButton().click();
      const request = backend.expectOne((candidate) => candidate.url === PDF);
      request.flush(new Blob(['%PDF'], { type: 'application/pdf' }));
      await settle();

      expect(exportButton().disabled).toBe(false);
      expect(clean(exportButton())).toBe('download Exportar informe operativo');
    });

    it('waits for the report to load before exporting', async () => {
      open();
      backend.expectOne('/api/reports/physiotherapists').flush(FAKE_PHYSIOTHERAPISTS);
      await settle();

      expect(exportButton().disabled).toBe(true);
      await answer();
      expect(exportButton().disabled).toBe(false);
    });

    it('shows the message of the API when the PDF cannot be generated', async () => {
      await openAndAnswer();

      exportButton().click();
      await settle();
      backend
        .expectOne((candidate) => candidate.url === PDF)
        .flush(
          new Blob(
            [JSON.stringify({ title: 'Error interno', detail: 'Ocurrió un error inesperado.' })],
            {
              type: 'application/problem+json',
            },
          ),
          { status: 500, statusText: 'Server Error' },
        );

      await vi.waitFor(async () => {
        await settle();
        expect(text('.export-error span')).toBe('Ocurrió un error inesperado.');
      });
      expect(element.querySelector('.export-error')?.getAttribute('role')).toBe('alert');
      expect(download.save).not.toHaveBeenCalled();
      expect(exportButton().disabled).toBe(false);
      // The report itself is still there
      expect(cards()).toHaveLength(5);
    });
  });
});
