import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { fakeSchedule } from '../../../shared/schedules/schedule-testing';
import { fakeBoard, fakeOperationsPage, fakeOperationsTreatment } from './assignments-testing';
import { SchedulesPage } from './schedules-page';

describe('SchedulesPage (MEJ-04)', () => {
  let fixture: ComponentFixture<SchedulesPage>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const button = (text: string) =>
    Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes(text))!;

  /** A pending rxResource never lets the page become stable: send the requests, answer them, then wait. */
  async function answer(boardTimes = 1, listParams: Record<string, string> = {}): Promise<void> {
    TestBed.tick();
    const boards = backend.match('/api/operations/schedules');
    expect(boards.length).toBe(boardTimes);
    boards.forEach((request) => request.flush(fakeBoard()));
    const lists = backend.match((req) => req.url === '/api/operations/treatments');
    expect(lists.length).toBe(1);
    for (const [name, value] of Object.entries(listParams)) {
      expect(lists[0].request.params.get(name)).toBe(value);
    }
    lists[0].flush(fakeOperationsPage([fakeOperationsTreatment(30)], 12));
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SchedulesPage);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    TestBed.tick();
    backend.expectOne('/api/operations/schedules').flush(fakeBoard());
    TestBed.tick();
    backend
      .expectOne((req) => req.url === '/api/operations/treatments')
      .flush(fakeOperationsPage([fakeOperationsTreatment(30)], 12));
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('shows the board and the active treatments, and says there is no clinical data', () => {
    expect(element.querySelector('h1')?.textContent).toContain('Horarios y asignaciones');
    expect(element.querySelector('.mode-notice')?.textContent).toContain(
      'Esta vista no muestra datos clínicos',
    );
    expect(element.querySelectorAll('app-schedule-board tbody tr').length).toBe(4);
    expect(element.querySelector('app-assignment-list')?.textContent).toContain(
      'Tratamientos activos (12)',
    );
  });

  it('filters the treatments with an inactive responsible from the first page', async () => {
    const toggle = Array.from(
      element.querySelectorAll<HTMLButtonElement>('mat-button-toggle button'),
    ).find((b) => b.textContent?.includes('Responsable inactivo'))!;
    toggle.click();
    fixture.detectChanges();
    TestBed.tick();

    const request = backend.expectOne((req) => req.url === '/api/operations/treatments');
    expect(request.request.params.get('inactiveResponsible')).toBe('true');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(fakeOperationsPage([]));
    await fixture.whenStable();

    expect(element.textContent).toContain(
      'Ningún tratamiento activo tiene un responsable inactivo.',
    );
  });

  it('reloads the board and the list after a reassignment', async () => {
    const updated = fakeOperationsTreatment(30, {
      responsible: { id: 4, fullName: 'Andrés Paredes Salinas' },
    });
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(updated),
    } as never);
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');

    button('Reasignar').click();
    await answer();

    expect(open).toHaveBeenCalled();
    expect(snackBar).toHaveBeenCalledWith(
      'Responsable actualizado: Andrés Paredes Salinas.',
      'Cerrar',
      { duration: 5000 },
    );
  });

  it('edits a schedule from the board and reloads it', async () => {
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(fakeSchedule(102, { fullName: 'Martín Cevallos Ruiz' })),
    } as never);
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');

    element
      .querySelector<HTMLButtonElement>('[aria-label="Editar horario de Martín Cevallos Ruiz"]')!
      .click();
    await answer();

    const data = open.mock.calls[0][1]?.data as { schedule: { staffId: number; slots: unknown[] } };
    expect(data.schedule.staffId).toBe(102);
    expect(data.schedule.slots.length).toBe(2);
    expect(snackBar).toHaveBeenCalledWith(
      'Horario de Martín Cevallos Ruiz guardado correctamente.',
      'Cerrar',
      { duration: 5000 },
    );
  });

  it('offers to retry when the board cannot be loaded', async () => {
    const failing = TestBed.createComponent(SchedulesPage);
    failing.detectChanges();
    TestBed.tick();
    backend
      .expectOne('/api/operations/schedules')
      .flush({}, { status: 500, statusText: 'Server Error' });
    TestBed.tick();
    backend
      .match((req) => req.url === '/api/operations/treatments')
      .forEach((r) => r.flush(fakeOperationsPage([])));
    await failing.whenStable();

    expect((failing.nativeElement as HTMLElement).textContent).toContain(
      'No se pudieron cargar los horarios',
    );
  });
});
