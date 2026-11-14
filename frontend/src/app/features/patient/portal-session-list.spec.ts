import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PortalSession } from './portal-api';
import { PortalSessionList } from './portal-session-list';
import { fakeSession } from './portal-testing';

/** Five sessions in chronological order: three took place, one was rescheduled and one was a no-show. */
const SESSIONS: PortalSession[] = [
  fakeSession(),
  fakeSession({
    sequenceNumber: 2,
    sessionDate: '2026-10-08',
    sessionTime: '15:00:00',
    responsibleName: 'Lcdo. Mateo Ríos',
    painScore: 6,
    patientInstructions: null,
  }),
  fakeSession({
    sequenceNumber: 3,
    sessionDate: '2026-10-12',
    painScore: 6,
    status: 'RESCHEDULED',
    patientInstructions: null,
  }),
  fakeSession({
    sequenceNumber: 4,
    sessionDate: '2026-10-15',
    painScore: 5,
    status: 'NO_SHOW',
    patientInstructions: 'Reagendar la sesión esta semana',
  }),
  fakeSession({ sequenceNumber: 5, sessionDate: '2026-10-19', painScore: 3 }),
];

describe('PortalSessionList', () => {
  let fixture: ComponentFixture<PortalSessionList>;
  let element: HTMLElement;

  const clean = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
  const text = (selector: string) => clean(element.querySelector(selector));
  const headers = () => Array.from(element.querySelectorAll('thead th'));
  const rows = () => Array.from(element.querySelectorAll<HTMLElement>('tbody tr'));
  const cells = (row: HTMLElement) => Array.from(row.querySelectorAll<HTMLElement>('td'));
  const column = (index: number) => rows().map((row) => clean(cells(row)[index]));

  function render(sessions: PortalSession[]): void {
    fixture = TestBed.createComponent(PortalSessionList);
    fixture.componentRef.setInput('sessions', sessions);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  it('has the title, the subtitle and the five columns of the prototype', () => {
    render(SESSIONS);

    expect(text('.title')).toBe('Listado de sesiones');
    expect(text('.subtitle')).toBe(
      'Historial de asistencias, evolución del dolor y terapeuta a cargo',
    );
    expect(headers().map(clean)).toEqual([
      'Sesión y fecha',
      'Fisioterapeuta responsable',
      'Intensidad EVA',
      'Actividades realizadas',
      'Estado',
    ]);
    expect(text('caption')).toBe('Sesiones del tratamiento');
  });

  it('shows each session with its date, time and type, in the order it receives', () => {
    render(SESSIONS);

    expect(column(0)).toEqual([
      'Sesión 01 05/10/2026 · 08:30 Terapia manual',
      'Sesión 02 08/10/2026 · 15:00 Terapia manual',
      'Sesión 03 12/10/2026 · 08:30 Terapia manual',
      'Sesión 04 15/10/2026 · 08:30 Terapia manual',
      'Sesión 05 19/10/2026 · 08:30 Terapia manual',
    ]);
  });

  it('shows the physiotherapist, the EVA with its color and the activities of each session', () => {
    render(SESSIONS);

    expect(column(1).slice(0, 2)).toEqual(['Lcda. Valeria Andrade', 'Lcdo. Mateo Ríos']);
    expect(column(2)).toEqual(['7/10', '6/10', '6/10', '5/10', '3/10']);
    const painBadges = rows().map((row) => cells(row)[2].querySelector('app-status-badge')!);
    expect(painBadges.map((badge) => badge.className)).toEqual([
      'badge badge-danger',
      'badge badge-warning',
      'badge badge-warning',
      'badge badge-warning',
      'badge badge-success',
    ]);
    expect(clean(cells(rows()[1])[3])).toBe('Movilización lumbar y TENS 15 minutos');
  });

  it('shows the status in Spanish: Realizada, Reprogramada and No asistió', () => {
    render(SESSIONS);

    expect(column(4)).toEqual(['Realizada', 'Realizada', 'Reprogramada', 'No asistió', 'Realizada']);
    expect(element.textContent).not.toMatch(/COMPLETED|RESCHEDULED|NO_SHOW/);
  });

  it('counts only the sessions that took place in the total', () => {
    render(SESSIONS);
    expect(text('.total')).toBe('Total realizadas: 3');

    fixture.componentRef.setInput('sessions', SESSIONS.slice(2, 4));
    fixture.detectChanges();
    expect(text('.total')).toBe('Total realizadas: 0');
  });

  it('shows the instructions of a session below its activities, with the prefix "Indicación:"', () => {
    render(SESSIONS);

    const first = cells(rows()[0])[3];
    expect(clean(first.querySelector('.activities'))).toBe('Movilización lumbar y TENS 15 minutos');
    expect(clean(first.querySelector('.instruction'))).toBe('Indicación: Calor local 15 minutos');
    expect(clean(cells(rows()[3])[3].querySelector('.instruction'))).toBe(
      'Indicación: Reagendar la sesión esta semana',
    );
    // A session without instructions has no empty "Indicación:" line
    expect(cells(rows()[1])[3].querySelector('.instruction')).toBeNull();
    expect(element.querySelectorAll('.instruction')).toHaveLength(3);
  });

  it('says "No disponible" when the account of the physiotherapist no longer exists', () => {
    render([fakeSession({ responsibleName: null })]);

    expect(column(1)).toEqual(['No disponible']);
  });

  it('shows a message instead of rows when the treatment has no sessions', () => {
    render([]);

    expect(text('td.empty')).toBe('Aún no hay sesiones registradas en este tratamiento.');
    expect(text('.total')).toBe('Total realizadas: 0');
    expect(element.querySelector('.footer')).toBeNull();
  });

  it('shows ten sessions per page and moves to the next ones', () => {
    const many = Array.from({ length: 12 }, (_, index) =>
      fakeSession({ sequenceNumber: index + 1 }),
    );
    render(many);

    expect(rows()).toHaveLength(10);
    expect(text('.range')).toBe('Mostrando 1–10 de 12 sesiones');
    // The total counts every session, not only the page
    expect(text('.total')).toBe('Total realizadas: 12');

    element.querySelector<HTMLButtonElement>('button[aria-label="Página siguiente"]')!.click();
    fixture.detectChanges();

    expect(column(0).map((cell) => cell?.slice(0, 9))).toEqual(['Sesión 11', 'Sesión 12']);
  });

  /**
   * jsdom does not lay the page out, so it cannot measure widths. This test guards the structure that keeps the
   * table inside its card; the real widths at 1366 and 1920 px are measured in the browser when the screenshots
   * of the story are taken.
   */
  it('keeps the structure that lets the table fit its card: five columns and long texts that wrap', () => {
    const long = 'Movilización lumbar, TENS 15 minutos, ejercicios de control motor y estiramientos. ';
    render([
      fakeSession({
        activitiesPerformed: long.repeat(4),
        patientInstructions: 'https://ejemplo.test/' + 'rutina-de-ejercicios-'.repeat(8),
        sessionType: 'Terapia manual y ejercicio terapéutico supervisado',
        responsibleName: 'Lcda. María Fernanda de los Ángeles Villavicencio Santamaría',
      }),
    ]);

    // Five columns, in one table inside the scroll container of the shared data table
    expect(headers()).toHaveLength(5);
    expect(cells(rows()[0])).toHaveLength(5);
    expect(element.querySelectorAll('app-data-table .scroll > table')).toHaveLength(1);
    // The headers of two words may break into lines; the short ones stay on one
    expect(headers().map((header) => header.classList.contains('wrap'))).toEqual([
      false,
      true,
      true,
      true,
      false,
    ]);
    // Every long text is in an element that wraps; only the short ones (session and date) stay on one line
    const [session, responsible, , activities] = cells(rows()[0]);
    expect(activities.querySelector('.activities')!.classList).toContain('wrap');
    expect(activities.querySelector('.instruction')!.classList).toContain('wrap');
    expect(responsible.querySelector('span')!.classList).toContain('wrap');
    expect(session.querySelector('.meta.wrap')!.textContent).toContain('Terapia manual y ejercicio');
    expect(session.querySelector('.session')!.classList).not.toContain('wrap');
    // No cell has an inline width that could force the table wider than its card
    for (const node of Array.from(element.querySelectorAll<HTMLElement>('table, th, td, td *'))) {
      expect(node.style.width).toBe('');
      expect(node.style.minWidth).toBe('');
    }
  });

  it('has no control to edit anything: only the pager', () => {
    render(SESSIONS);

    expect(element.querySelectorAll('a, input, select, textarea')).toHaveLength(0);
    const buttons = Array.from(element.querySelectorAll('button'));
    expect(buttons.map((button) => button.getAttribute('aria-label'))).toEqual([
      'Página anterior',
      'Página siguiente',
    ]);
    expect(element.textContent).not.toMatch(/Editar|Corregir|Registrar|Eliminar|Guardar/);
  });
});
