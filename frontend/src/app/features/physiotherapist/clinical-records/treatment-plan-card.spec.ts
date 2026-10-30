import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { ChangeResponsibleDialog } from './change-responsible-dialog';
import { ChangeStatusDialog } from './change-status-dialog';
import { TreatmentHistory } from './treatment-api';
import { TreatmentPlanCard } from './treatment-plan-card';
import { fakeHistory, fakeTreatment } from './treatment-testing';

const VALERIA = {
  physiotherapistId: 7,
  fullName: 'Lcda. Valeria Andrade',
  validFrom: '2026-10-01T15:00:00Z',
  validTo: '2026-10-20T15:00:00Z',
};
const MATEO = {
  physiotherapistId: 8,
  fullName: 'Lcdo. Mateo Ríos',
  validFrom: '2026-10-20T15:00:00Z',
  validTo: null,
};

describe('TreatmentPlanCard', () => {
  let fixture: ComponentFixture<TreatmentPlanCard>;
  let element: HTMLElement;
  let backend: HttpTestingController;
  const dialog = { open: vi.fn() };
  const snackBar = { open: vi.fn() };

  const buttons = () =>
    Object.fromEntries(
      Array.from(element.querySelectorAll<HTMLButtonElement>('.actions button')).map((b) => [
        b.textContent!.replace(/\s+/g, ' ').replace(/^\S+ /, '').trim(),
        b,
      ]),
    );
  const text = () => element.textContent!.replace(/\s+/g, ' ');

  async function show(history: TreatmentHistory): Promise<void> {
    fixture.componentRef.setInput('treatment', fakeTreatment(30, { status: history.status }));
    // Starts the history request; whenStable would wait for the pending resource
    TestBed.tick();
    backend.expectOne('/api/treatments/30/history').flush(history);
    await fixture.whenStable();
  }

  beforeEach(() => {
    dialog.open.mockReset();
    snackBar.open.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TreatmentPlanCard);
    element = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => backend.verify());

  it('shows the plan, the status and the current responsible', async () => {
    await show(fakeHistory(30));

    expect(element.querySelector('h2')?.textContent).toContain(
      'Plan terapéutico y asignación clínica',
    );
    expect(element.querySelector('.chip')?.textContent?.trim()).toBe('12 Sesiones Planificadas');
    expect(text()).toContain('Reducción del dolor y fortalecimiento lumbar progresivo');
    expect(text()).toContain('M54.4 Lumbago con ciática');
    expect(text()).toContain('3 veces por semana');
    expect(element.querySelector('.status')?.textContent?.trim()).toBe('Activo');
    expect(element.querySelector('.person-name')?.textContent?.trim()).toBe(
      'Lcda. Valeria Andrade',
    );
    expect(element.querySelector('.avatar')?.textContent?.trim()).toBe('VA');
    expect(text()).toContain('Sin cambios de estado registrados.');
    expect(buttons()['Cambiar responsable'].disabled).toBe(false);
    expect(buttons()['Cambiar estado'].disabled).toBe(false);
  });

  it('lists the responsibles and the status changes, most recent first', async () => {
    await show(
      fakeHistory(30, {
        currentResponsible: MATEO,
        responsibles: [MATEO, VALERIA],
        statusChanges: [
          {
            id: 2,
            previousStatus: 'ABANDONED',
            newStatus: 'ACTIVE',
            reason: 'Retoma la terapia',
            changedById: 8,
            changedByName: 'Lcdo. Mateo Ríos',
            changedAt: '2026-10-25T15:00:00Z',
          },
          {
            id: 1,
            previousStatus: 'ACTIVE',
            newStatus: 'ABANDONED',
            reason: 'No asiste desde hace un mes',
            changedById: 7,
            changedByName: 'Lcda. Valeria Andrade',
            changedAt: '2026-10-22T15:00:00Z',
          },
        ],
      }),
    );

    const [responsibles, changes] = Array.from(element.querySelectorAll('.timeline')).map((list) =>
      Array.from(list.querySelectorAll('li')).map((li) => li.textContent!.replace(/\s+/g, ' ')),
    );
    expect(responsibles[0]).toContain('Lcdo. Mateo Ríos');
    expect(responsibles[0]).toContain('Vigente');
    expect(responsibles[1]).toContain('Lcda. Valeria Andrade');
    expect(responsibles[1]).toContain('—');
    expect(changes[0]).toMatch(/^\s*Abandonado.*pasó a\s*Activo/);
    expect(changes[0]).toContain('Retoma la terapia');
    expect(changes[0]).toContain('Lcdo. Mateo Ríos ·');
    expect(changes[1]).toMatch(/^\s*Activo.*pasó a\s*Abandonado/);
  });

  it('locks both actions of a completed treatment, a final status', async () => {
    await show(fakeHistory(30, { status: 'COMPLETED', allowedNextStatuses: [] }));

    expect(text()).toContain('Tratamiento finalizado: es un estado final.');
    expect(buttons()['Cambiar responsable'].disabled).toBe(true);
    expect(buttons()['Cambiar estado'].disabled).toBe(true);
  });

  it('only allows resuming an abandoned treatment', async () => {
    await show(fakeHistory(30, { status: 'ABANDONED', allowedNextStatuses: ['ACTIVE'] }));

    expect(buttons()['Cambiar responsable'].disabled).toBe(true);
    expect(buttons()['Cambiar estado'].disabled).toBe(false);
  });

  it('opens the status dialog with the allowed statuses and shows the result', async () => {
    await show(fakeHistory(30));
    const completed = fakeHistory(30, { status: 'COMPLETED', allowedNextStatuses: [] });
    dialog.open.mockReturnValue({ afterClosed: () => of(completed) });

    buttons()['Cambiar estado'].click();
    await fixture.whenStable();

    expect(dialog.open).toHaveBeenCalledWith(
      ChangeStatusDialog,
      expect.objectContaining({
        data: {
          treatmentId: 30,
          currentStatus: 'ACTIVE',
          allowedStatuses: ['COMPLETED', 'ABANDONED'],
        },
      }),
    );
    expect(element.querySelector('.status')?.textContent?.trim()).toBe('Finalizado');
    expect(snackBar.open).toHaveBeenCalledWith('Estado del tratamiento actualizado.', 'Cerrar', {
      duration: 5000,
    });
  });

  it('opens the responsible dialog and keeps the card when it is cancelled', async () => {
    await show(fakeHistory(30));
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

    buttons()['Cambiar responsable'].click();
    await fixture.whenStable();

    expect(dialog.open).toHaveBeenCalledWith(
      ChangeResponsibleDialog,
      expect.objectContaining({
        data: {
          treatmentId: 30,
          currentResponsibleId: 7,
          currentResponsibleName: 'Lcda. Valeria Andrade',
        },
      }),
    );
    expect(element.querySelector('.person-name')?.textContent?.trim()).toBe(
      'Lcda. Valeria Andrade',
    );
    expect(snackBar.open).not.toHaveBeenCalled();
  });
});
