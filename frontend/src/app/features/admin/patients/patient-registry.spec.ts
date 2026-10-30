import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { PatientRegistry } from './patient-registry';
import { fakePatient } from './patient-testing';

describe('PatientRegistry', () => {
  let fixture: ComponentFixture<PatientRegistry>;
  let element: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(PatientRegistry);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  const registerButton = () =>
    Array.from(element.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Registrar Nuevo Paciente'),
    ) as HTMLButtonElement;

  it('shows the page header with the registration action', () => {
    expect(element.querySelector('h1')?.textContent).toContain('Registro y Control de Pacientes');
    expect(registerButton()).toBeTruthy();
  });

  it('opens the registered patient with a success message', async () => {
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(fakePatient(25)),
    } as never);
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    registerButton().click();
    await fixture.whenStable();

    expect(snackBar).toHaveBeenCalledWith('Paciente registrado correctamente.', 'Cerrar', {
      duration: 5000,
    });
    expect(navigate).toHaveBeenCalledWith(['/admin/patients', 25]);
  });

  it('does nothing when the dialog is cancelled', async () => {
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(undefined),
    } as never);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');

    registerButton().click();
    await fixture.whenStable();

    expect(navigate).not.toHaveBeenCalled();
  });
});
