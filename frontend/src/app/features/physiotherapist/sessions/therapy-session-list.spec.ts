import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { logInForTest } from '../../../core/auth/auth-testing';
import { fakeTreatment } from '../clinical-records/treatment-testing';
import { fakeSession, fakeVersions, pageOf } from './session-testing';
import { TherapySessionList } from './therapy-session-list';

describe('TherapySessionList', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let element: HTMLElement;

  const rows = () => Array.from(element.querySelectorAll('tbody tr'));
  const links = (label: string) =>
    // The text of an icon (e.g. "add") comes before the label
    Array.from(element.querySelectorAll('a')).filter((a) => a.textContent?.trim().endsWith(label));
  /** The row actions are icon buttons named by their aria-label. */
  const actions = (name: string) =>
    Array.from(element.querySelectorAll<HTMLElement>(`tbody [aria-label^="${name}"]`));

  async function settle(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  async function open(status: 'ACTIVE' | 'COMPLETED' = 'ACTIVE', totalElements = 2): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/physio/patients/15/treatments/30/sessions', TherapySessionList);
    element = harness.routeNativeElement as HTMLElement;
    backend.expectOne('/api/treatments/30').flush(fakeTreatment(30, { status }));
    const list = backend.expectOne((r) => r.url === '/api/treatments/30/therapy-sessions');
    expect(list.request.params.get('page')).toBe('0');
    list.flush(
      pageOf(
        [
          fakeSession(90),
          fakeSession(91, {
            sequenceNumber: 2,
            sessionDate: '2026-11-05',
            painScore: 8,
            status: 'NO_SHOW',
            progressStatus: 'NEEDS_ATTENTION',
            responsible: { id: 8, fullName: 'Lcdo. Mateo Ríos' },
            updatedAt: '2026-11-05T20:00:00Z',
          }),
        ],
        totalElements,
      ),
    );
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'physio/patients/:patientId/treatments/:treatmentId/sessions',
            component: TherapySessionList,
          },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    logInForTest('PHYSIOTHERAPIST', 'Lcda. Valeria Andrade', 7);
  });

  afterEach(() => backend.verify());

  it('lists the sessions by number with their EVA, status, progress and responsible', async () => {
    await open();

    expect(element.querySelector('.treatment')?.textContent).toContain('M54.4');
    const [first, second] = rows().map((row) => row.textContent!.replace(/\s+/g, ' '));
    expect(first).toContain('N.º 1');
    expect(first).toContain('04/11/2026');
    expect(first).toContain('09:30');
    expect(first).toContain('4/10');
    expect(first).toContain('Realizada');
    expect(first).toContain('En mejoría');
    expect(first).toContain('Lcda. Valeria Andrade');
    expect(second).toContain('N.º 2');
    expect(second).toContain('No asistió');
    expect(second).toContain('Requiere atención');
    expect(second).toContain('Corregida el 05/11/2026');
    expect(second).toContain('Lcdo. Mateo Ríos');
  });

  it('offers "Corregir" only on the sessions of the authenticated physiotherapist', async () => {
    await open();

    const correct = actions('Corregir');
    expect(correct).toHaveLength(1);
    expect(correct[0].getAttribute('href')).toBe(
      '/physio/patients/15/treatments/30/sessions/90/edit',
    );
    expect(correct[0].getAttribute('aria-label')).toBe('Corregir la sesión N.º 1');
    expect(actions('Ver versiones')).toHaveLength(2);
  });

  it('offers to register a session only while the treatment is active', async () => {
    await open();
    expect(links('Registrar sesión')[0]?.getAttribute('href')).toBe(
      '/physio/patients/15/treatments/30/sessions/new',
    );
  });

  it('does not offer to register a session on a closed treatment', async () => {
    await open('COMPLETED');
    expect(links('Registrar sesión')).toHaveLength(0);
  });

  it('opens the timeline of the treatment, whatever its status', async () => {
    await open('COMPLETED');
    expect(links('Línea de tiempo')[0]?.getAttribute('href')).toBe(
      '/physio/patients/15/treatments/30/timeline',
    );
  });

  it('loads the next page from the server', async () => {
    await open('ACTIVE', 12);

    element.querySelector<HTMLButtonElement>('button[aria-label="Página siguiente"]')!.click();
    await settle();

    const next = backend.expectOne((r) => r.url === '/api/treatments/30/therapy-sessions');
    expect(next.request.params.get('page')).toBe('1');
    next.flush(pageOf([fakeSession(100, { sequenceNumber: 11 })], 12, 1));
    await settle();
    expect(rows()[0].textContent).toContain('N.º 11');
  });

  it('opens the versions of a session', async () => {
    await open();

    actions('Ver versiones')[1].click();
    await settle();

    backend.expectOne('/api/therapy-sessions/91/versions').flush(fakeVersions());
    await settle();
    const dialog = document.querySelector('app-session-versions-dialog')!;
    expect(dialog.textContent).toContain('Versiones de la sesión N.º 2');
    expect(dialog.textContent).toContain('Corrección');
  });

  it('tells when the treatment does not exist', async () => {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/physio/patients/15/treatments/404/sessions');
    element = harness.routeNativeElement as HTMLElement;
    const notFound = { status: 404, statusText: 'Not Found' };
    backend
      .expectOne('/api/treatments/404')
      .flush({ detail: 'El tratamiento no existe.' }, notFound);
    backend
      .expectOne((r) => r.url === '/api/treatments/404/therapy-sessions')
      .flush({ detail: 'El tratamiento no existe.' }, notFound);
    await settle();

    expect(element.textContent).toContain('No se pudieron cargar las sesiones');
    expect(element.textContent).toContain('El tratamiento no existe.');
  });
});
