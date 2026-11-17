import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NO_REPORT_FILTERS, periodLabel, ReportApi } from './report-api';
import {
  FAKE_PHYSIOTHERAPISTS,
  fakeReportTreatment,
  fakeSummary,
  fakeTreatmentPage,
} from './report-testing';

describe('ReportApi', () => {
  let api: ReportApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ReportApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('asks for the summary without parameters when no filter is chosen', () => {
    api.summary(NO_REPORT_FILTERS).subscribe((summary) => expect(summary.sessions.total).toBe(28));

    const call = backend.expectOne((request) => request.url === '/api/reports/summary');
    expect(call.request.method).toBe('GET');
    expect(call.request.params.keys()).toEqual([]);
    call.flush(fakeSummary());
  });

  it('sends only the filters that were chosen', () => {
    api.summary({ ...NO_REPORT_FILTERS, status: 'COMPLETED', physiotherapistId: 4 }).subscribe();
    api.summary({ ...NO_REPORT_FILTERS, physiotherapistId: 4 }).subscribe();
    api.summary({ ...NO_REPORT_FILTERS, from: '2026-10-01', to: '2026-10-31' }).subscribe();
    api.summary({ ...NO_REPORT_FILTERS, to: '2026-10-31' }).subscribe();

    const [both, onlyPhysiotherapist, range, onlyTo] = backend.match(
      (request) => request.url === '/api/reports/summary',
    );
    expect(both.request.params.get('status')).toBe('COMPLETED');
    expect(both.request.params.get('physiotherapistId')).toBe('4');
    expect(onlyPhysiotherapist.request.params.keys()).toEqual(['physiotherapistId']);
    expect(range.request.params.get('from')).toBe('2026-10-01');
    expect(range.request.params.get('to')).toBe('2026-10-31');
    expect(onlyTo.request.params.keys()).toEqual(['to']);
    [both, onlyPhysiotherapist, range, onlyTo].forEach((call) => call.flush(fakeSummary()));
  });

  it('downloads the PDF with the same filters, as a Blob', () => {
    const pdf = new Blob(['%PDF-1.4'], { type: 'application/pdf' });
    let received: Blob | undefined;
    api
      .exportPdf({ status: 'ABANDONED', physiotherapistId: 3, from: '2026-10-01', to: null })
      .subscribe((blob) => (received = blob));

    const call = backend.expectOne((request) => request.url === '/api/reports/summary.pdf');
    expect(call.request.method).toBe('GET');
    expect(call.request.responseType).toBe('blob');
    expect(call.request.params.keys().sort()).toEqual(['from', 'physiotherapistId', 'status']);
    call.flush(pdf);
    expect(received).toBe(pdf);
  });

  it('names the period like the PDF', () => {
    expect(periodLabel({ from: '2026-10-01', to: '2026-10-31' })).toBe(
      'Del 01/10/2026 al 31/10/2026',
    );
    expect(periodLabel({ from: '2026-10-01', to: null })).toBe('Desde el 01/10/2026');
    expect(periodLabel({ from: null, to: '2026-10-31' })).toBe('Hasta el 31/10/2026');
    expect(periodLabel({ from: null, to: null })).toBeNull();
  });

  it('asks for one page of treatments with the filters', () => {
    api
      .treatments({ ...NO_REPORT_FILTERS, status: 'ACTIVE' }, 2, 10)
      .subscribe((page) => expect(page.content.map((t) => t.id)).toEqual([30]));

    const call = backend.expectOne((request) => request.url === '/api/reports/treatments');
    expect(call.request.method).toBe('GET');
    expect(call.request.params.get('status')).toBe('ACTIVE');
    expect(call.request.params.has('physiotherapistId')).toBe(false);
    expect(call.request.params.get('page')).toBe('2');
    expect(call.request.params.get('size')).toBe('10');
    call.flush(fakeTreatmentPage([fakeReportTreatment()]));
  });

  it('lists the physiotherapists of the filter', () => {
    api.physiotherapists().subscribe((options) => expect(options).toEqual(FAKE_PHYSIOTHERAPISTS));

    const call = backend.expectOne('/api/reports/physiotherapists');
    expect(call.request.method).toBe('GET');
    call.flush(FAKE_PHYSIOTHERAPISTS);
  });
});
