import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSelectHarness } from '@angular/material/select/testing';
import { ChangeStatusDialog, ChangeStatusDialogData } from './change-status-dialog';
import { TreatmentStatus } from './clinical-record-api';
import { fakeHistory } from './treatment-testing';

const ALLOWED_FROM: Record<TreatmentStatus, TreatmentStatus[]> = {
  ACTIVE: ['COMPLETED', 'ABANDONED'],
  ABANDONED: ['ACTIVE'],
  COMPLETED: [],
};

describe('ChangeStatusDialog', () => {
  let fixture: ComponentFixture<ChangeStatusDialog>;
  let element: HTMLElement;
  let loader: HarnessLoader;
  let backend: HttpTestingController;
  const dialogRef = { close: vi.fn() };

  const form = () => fixture.componentInstance['form'];
  const errors = () =>
    Array.from(element.querySelectorAll('mat-error')).map((error) => error.textContent?.trim());

  async function open(currentStatus: TreatmentStatus): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            treatmentId: 30,
            currentStatus,
            allowedStatuses: ALLOWED_FROM[currentStatus],
          } satisfies ChangeStatusDialogData,
        },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChangeStatusDialog);
    element = fixture.nativeElement as HTMLElement;
    loader = TestbedHarnessEnvironment.loader(fixture);
    await fixture.whenStable();
  }

  async function submit(): Promise<void> {
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  async function options(): Promise<string[]> {
    const select = await loader.getHarness(MatSelectHarness);
    await select.open();
    return Promise.all((await select.getOptions()).map((option) => option.getText()));
  }

  beforeEach(() => dialogRef.close.mockReset());

  afterEach(() => backend.verify());

  it('offers only Finalizado and Abandonado for an active treatment', async () => {
    await open('ACTIVE');

    expect(element.querySelector('.subtitle')?.textContent).toContain('Estado actual: Activo');
    expect(await options()).toEqual(['Finalizado', 'Abandonado']);
    expect(form().controls.newStatus.value).toBeNull();
  });

  it('offers only Activo for an abandoned treatment, already chosen', async () => {
    await open('ABANDONED');

    expect(await options()).toEqual(['Activo']);
    expect(form().controls.newStatus.value).toBe('ACTIVE');
  });

  it('warns that Finalizado is a final status', async () => {
    await open('ACTIVE');
    form().controls.newStatus.setValue('COMPLETED');
    await fixture.whenStable();

    expect(element.querySelector('.notice')?.textContent).toContain(
      'Finalizado es un estado final: si el paciente vuelve, se abre un tratamiento nuevo.',
    );
  });

  it('requires the new status and a reason that is not only spaces', async () => {
    await open('ACTIVE');
    form().controls.reason.setValue('   ');
    await submit();

    expect(errors()).toEqual([
      'Seleccione el nuevo estado.',
      'Ingrese el motivo del cambio de estado.',
    ]);
    backend.expectNone('/api/treatments/30/status');
  });

  it('sends the change with the trimmed reason and closes with the updated history', async () => {
    await open('ACTIVE');
    form().setValue({ newStatus: 'ABANDONED', reason: '  No asiste desde hace un mes  ' });
    await submit();

    const request = backend.expectOne('/api/treatments/30/status');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({
      newStatus: 'ABANDONED',
      reason: 'No asiste desde hace un mes',
    });
    const history = fakeHistory(30, { status: 'ABANDONED', allowedNextStatuses: ['ACTIVE'] });
    request.flush(history);

    expect(dialogRef.close).toHaveBeenCalledWith(history);
  });

  it('shows the Spanish detail of a rejected change', async () => {
    await open('ABANDONED');
    form().controls.reason.setValue('Retoma la terapia');
    await submit();

    backend
      .expectOne('/api/treatments/30/status')
      .flush(
        { status: 422, detail: 'El paciente ya tiene un tratamiento activo.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await fixture.whenStable();

    expect(element.querySelector('.error')?.textContent).toContain(
      'El paciente ya tiene un tratamiento activo.',
    );
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
