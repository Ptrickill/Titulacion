import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { RouterTestingHarness } from '@angular/router/testing';
import { PatientDetail } from './patient-detail';
import { fakePatient } from './patient-testing';

describe('PatientDetail', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;

  async function open(id: string): Promise<HTMLElement> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/admin/patients/${id}`, PatientDetail);
    return harness.routeNativeElement as HTMLElement;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'admin/patients/:id', component: PatientDetail }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('shows the admission and personal data with the calculated age', async () => {
    const element = await open('25');
    backend.expectOne('/api/patients/25').flush(fakePatient(25));
    harness.detectChanges();
    await harness.fixture.whenStable();

    expect(element.querySelector('h1')?.textContent).toContain('Juan Carlos Pérez Gómez');
    const text = element.textContent!;
    expect(text).toContain('Historia clínica N.º 1710034065');
    expect(text).toContain('15/03/1996');
    expect(text).toContain('(30 años)');
    expect(text).toContain('Masculino');
    expect(text).toContain('Mestizo');
    // Religion was not given
    expect(text).toContain('No especificada');
  });

  it('edits the data and shows the saved record with its last update', async () => {
    const element = await open('25');
    backend.expectOne('/api/patients/25').flush(fakePatient(25));
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(element.textContent).toContain('Sin cambios desde el registro');

    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () =>
        of(fakePatient(25, { phone: '0991112233', updatedAt: '2026-10-30T16:45:00Z' })),
    } as never);
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    Array.from(element.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('Editar datos'))!
      .click();
    harness.detectChanges();
    await harness.fixture.whenStable();

    expect(element.textContent).toContain('0991112233');
    expect(element.textContent).toContain('Última actualización');
    expect(element.textContent).not.toContain('Sin cambios desde el registro');
    expect(snackBar).toHaveBeenCalledWith(
      'Datos del paciente actualizados correctamente.',
      'Cerrar',
      { duration: 5000 },
    );
  });

  it('tells when the patient does not exist', async () => {
    const element = await open('999');
    backend
      .expectOne('/api/patients/999')
      .flush({ detail: 'El paciente no existe.' }, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();
    await harness.fixture.whenStable();

    expect(element.textContent).toContain('El paciente no existe');
    expect(element.querySelector('a[href="/admin/patients"]')).toBeTruthy();
  });

  it('does not call the API for an id that is not a number', async () => {
    const element = await open('abc');

    backend.expectNone(() => true);
    expect(element.textContent).toContain('El paciente no existe');
  });
});
