import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { Page } from '../../../core/http/page';
import { PatientSummary } from './patient-api';
import { PatientRegistry } from './patient-registry';
import { fakePatient } from './patient-testing';

function summary(id: number, firstName: string, lastName: string): PatientSummary {
  return {
    id,
    nationalId: '1710034065',
    medicalRecordNumber: '1710034065',
    firstName,
    lastName,
    age: 30,
    phone: '+593 98 765 4321',
    email: `paciente${id}@correo.ec`,
  };
}

function page(content: PatientSummary[], number = 0, totalElements = content.length) {
  return {
    content,
    page: number,
    size: 10,
    totalElements,
    totalPages: Math.ceil(totalElements / 10),
  } satisfies Page<PatientSummary>;
}

describe('PatientRegistry', () => {
  let fixture: ComponentFixture<PatientRegistry>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const searchRequest = (): TestRequest =>
    backend.expectOne((request) => request.url === '/api/patients');
  const rowNames = () =>
    Array.from(element.querySelectorAll('tbody .name')).map((cell) => cell.textContent?.trim());
  const button = (text: string) =>
    Array.from(element.querySelectorAll('button')).find((b) =>
      b.textContent?.includes(text),
    ) as HTMLButtonElement;

  async function typeSearch(text: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement>('input[type="search"]')!;
    input.value = text;
    input.dispatchEvent(new Event('input'));
    // Debounce of the search field
    await new Promise((resolve) => setTimeout(resolve, 350));
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PatientRegistry);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('lists the first page of patients with identification and contact data only', async () => {
    const request = searchRequest();
    expect(request.request.params.get('q')).toBe('');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('10');
    request.flush(page([summary(1, 'Juan Carlos', 'Pérez Gómez')], 0, 12));
    await fixture.whenStable();

    expect(element.querySelector('h1')?.textContent).toContain('Registro y Control de Pacientes');
    expect(rowNames()).toEqual(['Juan Carlos Pérez Gómez']);
    const row = element.querySelector('tbody tr')!.textContent!;
    expect(row).toContain('Cédula: 1710034065 • 30 años');
    expect(row).toContain('paciente1@correo.ec');
    expect(element.querySelector('.avatar')?.textContent?.trim()).toBe('JP');
    const headers = Array.from(element.querySelectorAll('th')).map((th) => th.textContent?.trim());
    expect(headers).toEqual(['Paciente', 'Contacto', 'Acciones']);
    expect(element.querySelector('.range')?.textContent?.replace(/\s+/g, ' ')).toContain(
      'de 12 pacientes',
    );
  });

  it('searches as the user types, from the first page', async () => {
    searchRequest().flush(page([summary(1, 'Ana', 'Benítez')], 0, 12));
    await fixture.whenStable();
    element.querySelector<HTMLButtonElement>('button[aria-label="Página siguiente"]')!.click();
    await fixture.whenStable();
    const second = searchRequest();
    expect(second.request.params.get('page')).toBe('1');
    second.flush(page([summary(2, 'Luis', 'Moreira')], 1, 12));
    await fixture.whenStable();
    expect(rowNames()).toEqual(['Luis Moreira']);

    await typeSearch('  perez ');
    const search = searchRequest();
    expect(search.request.params.get('q')).toBe('perez');
    expect(search.request.params.get('page')).toBe('0');
    search.flush(page([]));
    await fixture.whenStable();
    expect(element.querySelector('td.empty')?.textContent?.trim()).toBe(
      'Ningún paciente coincide con la búsqueda.',
    );
  });

  it('opens the edit form with the full data and reloads the page after saving', async () => {
    searchRequest().flush(page([summary(25, 'Juan Carlos', 'Pérez Gómez')]));
    await fixture.whenStable();
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(fakePatient(25, { phone: '0991112233' })),
    } as never);
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');

    button('Editar datos').click();
    backend.expectOne('/api/patients/25').flush(fakePatient(25));
    await fixture.whenStable();

    expect(open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { patient: fakePatient(25) } }),
    );
    expect(snackBar).toHaveBeenCalledWith(
      'Datos del paciente actualizados correctamente.',
      'Cerrar',
      { duration: 5000 },
    );
    const reload = searchRequest();
    expect(reload.request.params.get('page')).toBe('0');
    reload.flush(page([summary(25, 'Juan Carlos', 'Pérez Gómez')]));
  });

  it('tells when the patient data cannot be loaded for editing', async () => {
    searchRequest().flush(page([summary(25, 'Juan Carlos', 'Pérez Gómez')]));
    await fixture.whenStable();
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');

    button('Editar datos').click();
    backend
      .expectOne('/api/patients/25')
      .flush({ detail: 'El paciente no existe.' }, { status: 404, statusText: 'Not Found' });

    expect(snackBar).toHaveBeenCalledWith('El paciente no existe.', 'Cerrar', { duration: 6000 });
  });

  it('opens the registered patient with a success message', async () => {
    searchRequest().flush(page([]));
    await fixture.whenStable();
    expect(element.querySelector('td.empty')?.textContent?.trim()).toBe(
      'Aún no hay pacientes registrados.',
    );
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(fakePatient(30)),
    } as never);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    button('Registrar Nuevo Paciente').click();
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(['/admin/patients', 30]);
  });

  it('offers a retry when the search fails', async () => {
    searchRequest().flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    expect(element.textContent).toContain('No se pudieron cargar los pacientes');

    button('Reintentar').click();
    await fixture.whenStable();
    searchRequest().flush(page([summary(1, 'Ana', 'Benítez')]));
    await fixture.whenStable();
    expect(rowNames()).toEqual(['Ana Benítez']);
  });
});
