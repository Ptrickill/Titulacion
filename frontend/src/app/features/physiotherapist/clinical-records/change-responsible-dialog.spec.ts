import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSelectHarness } from '@angular/material/select/testing';
import { ChangeResponsibleDialog, ChangeResponsibleDialogData } from './change-responsible-dialog';
import { fakeHistory } from './treatment-testing';

const PHYSIOTHERAPISTS = [
  { id: 8, fullName: 'Lcdo. Mateo Ríos' },
  { id: 7, fullName: 'Lcda. Valeria Andrade' },
  { id: 9, fullName: 'Lcda. Paula Cevallos' },
];

describe('ChangeResponsibleDialog', () => {
  let fixture: ComponentFixture<ChangeResponsibleDialog>;
  let element: HTMLElement;
  let loader: HarnessLoader;
  let backend: HttpTestingController;
  const dialogRef = { close: vi.fn() };

  const form = () => fixture.componentInstance['form'];

  async function submit(): Promise<void> {
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  beforeEach(async () => {
    dialogRef.close.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            treatmentId: 30,
            currentResponsibleId: 7,
            currentResponsibleName: 'Lcda. Valeria Andrade',
          } satisfies ChangeResponsibleDialogData,
        },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChangeResponsibleDialog);
    element = fixture.nativeElement as HTMLElement;
    loader = TestbedHarnessEnvironment.loader(fixture);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('offers the active physiotherapists except the current responsible', async () => {
    backend.expectOne('/api/physio/physiotherapists').flush(PHYSIOTHERAPISTS);
    await fixture.whenStable();

    expect(element.querySelector('.subtitle')?.textContent).toContain(
      'Responsable vigente: Lcda. Valeria Andrade',
    );
    const select = await loader.getHarness(MatSelectHarness);
    await select.open();
    const options = await Promise.all((await select.getOptions()).map((o) => o.getText()));
    expect(options).toEqual(['Lcdo. Mateo Ríos', 'Lcda. Paula Cevallos']);
  });

  it('requires a choice, then assigns it and closes with the updated history', async () => {
    backend.expectOne('/api/physio/physiotherapists').flush(PHYSIOTHERAPISTS);
    await fixture.whenStable();

    await submit();
    expect(element.querySelector('mat-error')?.textContent).toContain(
      'Seleccione el nuevo responsable.',
    );
    backend.expectNone('/api/treatments/30/responsible');

    form().controls.physiotherapistId.setValue(8);
    await submit();
    const request = backend.expectOne('/api/treatments/30/responsible');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ physiotherapistId: 8 });
    const history = fakeHistory(30);
    request.flush(history);

    expect(dialogRef.close).toHaveBeenCalledWith(history);
  });

  it('tells when no other physiotherapist is active', async () => {
    backend.expectOne('/api/physio/physiotherapists').flush([PHYSIOTHERAPISTS[1]]);
    await fixture.whenStable();

    expect(element.querySelector('.notice')?.textContent).toContain(
      'No hay otros fisioterapeutas activos para asignar.',
    );
    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
  });

  it('shows the Spanish detail when the server rejects the physiotherapist', async () => {
    backend.expectOne('/api/physio/physiotherapists').flush(PHYSIOTHERAPISTS);
    await fixture.whenStable();
    form().controls.physiotherapistId.setValue(9);
    await submit();

    backend
      .expectOne('/api/treatments/30/responsible')
      .flush(
        { status: 422, detail: 'El nuevo responsable debe ser un fisioterapeuta activo.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await fixture.whenStable();

    expect(element.querySelector('.error')?.textContent).toContain(
      'El nuevo responsable debe ser un fisioterapeuta activo.',
    );
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
