import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { logInForTest } from '../../../core/auth/auth-testing';
import { fakePatient } from '../../admin/patients/patient-testing';
import { PainCurveChart } from './pain-curve-chart';
import { fakeTimeline, stubCanvas } from './session-testing';
import { TherapyTimeline } from './therapy-timeline';

describe('TherapyTimeline', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let element: HTMLElement;
  let restoreCanvas: () => void;

  const text = (selector: string) =>
    element.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const cards = () => Array.from(element.querySelectorAll<HTMLElement>('.timeline .entry'));
  const links = (label: string) =>
    // The text of an icon (e.g. "add") comes before the label
    Array.from(element.querySelectorAll('a')).filter((a) => a.textContent?.trim().endsWith(label));
  const timelineCall = () => backend.expectOne((r) => r.url === '/api/treatments/30/timeline');
  const input = (name: 'from' | 'to') =>
    element.querySelector<HTMLInputElement>(`input[formcontrolname="${name}"]`)!;

  async function settle(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  async function open(timeline = fakeTimeline()): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/physio/patients/15/treatments/30/timeline', TherapyTimeline);
    element = harness.routeNativeElement as HTMLElement;
    backend.expectOne('/api/patients/15').flush(fakePatient(15));
    const call = timelineCall();
    expect(call.request.params.keys()).toEqual([]);
    call.flush(timeline);
    await settle();
  }

  async function type(name: 'from' | 'to', value: string): Promise<void> {
    input(name).value = value;
    input(name).dispatchEvent(new Event('input'));
    await settle();
  }

  beforeEach(() => {
    restoreCanvas = stubCanvas();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'physio/patients/:patientId/treatments/:treatmentId/timeline',
            component: TherapyTimeline,
          },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    logInForTest('PHYSIOTHERAPIST', 'Lcda. Valeria Andrade', 7);
  });

  afterEach(() => {
    backend.verify();
    restoreCanvas();
  });

  it('shows the patient, the diagnosis and the progress of the plan', async () => {
    await open();

    const bar = text('.patient-bar');
    expect(bar).toContain('1710034065');
    expect(bar).toContain('Paciente: Juan Carlos Pérez Gómez');
    expect(bar).toContain('Patología: M51.2 Hernia discal lumbar');
    expect(bar).toContain('EVA inicial: 8/10');
    expect(bar).toContain('Progreso: 2 / 12 Sesiones Planificadas');
  });

  it('shows a card per session in the order of the server with its EVA, status, notes and responsible', async () => {
    await open();

    const [first, second, third] = cards().map((card) => card.textContent!.replace(/\s+/g, ' '));
    expect(cards()).toHaveLength(3);
    expect(first).toContain('Sesión 01');
    expect(first).toContain('03 de');
    expect(first).toContain('09:30');
    expect(first).toContain('Fisioterapia lumbar mecánica');
    expect(first).toContain('Lcda. Valeria Andrade');
    expect(first).toContain('EVA: 6/10');
    expect(first).toContain('Realizada');
    expect(first).toContain('En mejoría');
    expect(first).toContain('Movilización lumbar y TENS 15 minutos');
    expect(first).toContain('Buena tolerancia al ejercicio');
    expect(first).toContain('Calor local 15 minutos en casa');
    expect(second).toContain('Sesión 02');
    expect(second).toContain('No asistió');
    expect(second).toContain('Lcdo. Mateo Ríos');
    expect(second).toContain('Sin indicaciones para el paciente.');
    expect(third).toContain('Sesión 03');
    expect(third).toContain('Terapia manual y movilidad lumbar');
  });

  it('draws the pain curve the server sends', async () => {
    await open();

    const chart = harness.fixture.debugElement.query(By.directive(PainCurveChart));
    expect(chart).not.toBeNull();
    expect((chart.componentInstance as PainCurveChart).points().map((p) => p.label)).toEqual([
      'Evaluación inicial',
      'Sesión 1',
      'Sesión 3',
    ]);
  });

  it('offers to register a session on an active treatment and links the session list', async () => {
    await open();

    expect(links('Registrar Sesión de Hoy')[0]?.getAttribute('href')).toBe(
      '/physio/patients/15/treatments/30/sessions/new',
    );
    expect(links('Ver sesiones')[0]?.getAttribute('href')).toBe(
      '/physio/patients/15/treatments/30/sessions',
    );
  });

  it('does not offer to register a session on a treatment that is not active', async () => {
    const closed = fakeTimeline();
    closed.treatment.active = false;
    await open(closed);

    expect(links('Registrar Sesión de Hoy')).toHaveLength(0);
    expect(links('Ver sesiones')).toHaveLength(1);
  });

  it('reloads with each date of the filter', async () => {
    await open();

    await type('from', '2026-10-05');
    let call = timelineCall();
    expect(call.request.params.get('from')).toBe('2026-10-05');
    expect(call.request.params.has('to')).toBe(false);
    call.flush(fakeTimeline({ from: '2026-10-05', sessions: fakeTimeline().sessions.slice(1) }));
    await settle();
    expect(cards()).toHaveLength(2);

    await type('to', '2026-10-06');
    call = timelineCall();
    expect(call.request.params.get('from')).toBe('2026-10-05');
    expect(call.request.params.get('to')).toBe('2026-10-06');
    call.flush(fakeTimeline({ sessions: fakeTimeline().sessions.slice(1, 2) }));
    await settle();
    expect(cards()).toHaveLength(1);
  });

  it('does not search an inverted range and only shows the notice', async () => {
    await open();

    await type('from', '2026-10-09');
    timelineCall().flush(fakeTimeline());
    await settle();
    await type('to', '2026-10-08');

    backend.expectNone((r) => r.url === '/api/treatments/30/timeline');
    expect(text('[role="alert"]')).toBe(
      'La fecha inicial no puede ser posterior a la fecha final.',
    );
    expect(cards()).toHaveLength(0);
  });

  it('clears the filter and loads the whole timeline again', async () => {
    await open();
    await type('from', '2026-10-05');
    timelineCall().flush(fakeTimeline({ sessions: [] }));
    await settle();

    const clear = Array.from(element.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Limpiar'),
    )!;
    clear.click();
    await settle();

    const call = timelineCall();
    expect(call.request.params.keys()).toEqual([]);
    call.flush(fakeTimeline());
    await settle();
    expect(input('from').value).toBe('');
    expect(cards()).toHaveLength(3);
  });

  it('says when a treatment has no sessions yet', async () => {
    await open(fakeTimeline({ sessions: [], painCurve: fakeTimeline().painCurve.slice(0, 1) }));

    expect(text('app-empty-state')).toContain('Aún no hay sesiones registradas');
    expect(element.querySelector('app-pain-curve-chart')).toBeNull();
    expect(links('Registrar sesión')).toHaveLength(1);
  });

  it('says when the range has no sessions', async () => {
    await open();
    await type('from', '2026-11-01');
    timelineCall().flush(fakeTimeline({ sessions: [], painCurve: [] }));
    await settle();

    expect(text('app-empty-state')).toContain('No hay sesiones en ese rango de fechas');
    expect(cards()).toHaveLength(0);
  });

  it('explains that a range without completed sessions has nothing to draw', async () => {
    const onlyNoShow = fakeTimeline();
    await open({
      ...onlyNoShow,
      sessions: onlyNoShow.sessions.slice(1, 2),
      painCurve: onlyNoShow.painCurve.slice(0, 1),
    });

    expect(element.querySelector('app-pain-curve-chart')).toBeNull();
    expect(text('.no-curve')).toBe('No hay sesiones realizadas en este rango para graficar.');
    expect(cards()).toHaveLength(1);
  });

  it('shows the message of a treatment that does not exist', async () => {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/physio/patients/15/treatments/30/timeline', TherapyTimeline);
    element = harness.routeNativeElement as HTMLElement;
    backend.expectOne('/api/patients/15').flush(fakePatient(15));
    timelineCall().flush(
      { title: 'Recurso no encontrado', detail: 'El tratamiento no existe.' },
      { status: 404, statusText: 'Not Found' },
    );
    await settle();

    expect(text('app-empty-state')).toContain('El tratamiento no existe.');
  });

  it('refuses a treatment of another patient', async () => {
    const other = fakeTimeline();
    other.treatment.patientId = 99;
    await open(other);

    expect(text('app-empty-state')).toContain('El tratamiento no pertenece a este paciente.');
    expect(cards()).toHaveLength(0);
  });
});
