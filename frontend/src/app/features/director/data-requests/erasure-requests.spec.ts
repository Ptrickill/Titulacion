import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { fakeDataRequest } from '../../../shared/legal/legal-testing';
import { ErasureRequest } from './erasure-api';
import { ErasureRequests } from './erasure-requests';

const ERASURE: ErasureRequest = {
  request: fakeDataRequest(9, { type: 'ERASURE', daysElapsed: 3 }),
  patientId: 25,
  patientName: 'Rosa Elena Ortiz Vega',
  nationalId: '1710034065',
};

/** MEJ-06: only the operations director accepts an erasure, confirming it with the national ID. */
describe('ErasureRequests', () => {
  let fixture: ComponentFixture<ErasureRequests>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const button = (text: string) =>
    Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes(text));

  async function render(list: ErasureRequest[]): Promise<void> {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ErasureRequests);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    TestBed.tick();
    backend.expectOne('/api/data-requests/erasures').flush(list);
    await fixture.whenStable();
  }

  afterEach(() => backend.verify());

  it('lists the open erasures with the patient, its national ID and the due date', async () => {
    await render([ERASURE]);

    const item = element.querySelector('.item')!;
    expect(item.textContent).toContain('Rosa Elena Ortiz Vega');
    expect(item.textContent).toContain('Cédula 1710034065');
    expect(item.textContent).toContain('hace 3 días');
    expect(item.textContent).toContain('responder hasta el 05/12/2026');
  });

  it('accepts an erasure with the typed national ID and the response', async () => {
    await render([ERASURE]);
    const dialog = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () =>
        of({ decision: 'accept', nationalId: '1710034065', response: 'Se bloquearon sus datos.' }),
    } as never);

    button('Aceptar eliminación')!.click();
    await fixture.whenStable();

    expect(dialog).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { erasure: ERASURE, decision: 'accept' } }),
    );
    const request = backend.expectOne('/api/data-requests/erasures/9/acceptance');
    expect(request.request.body).toEqual({
      nationalId: '1710034065',
      response: 'Se bloquearon sus datos.',
    });
    request.flush(fakeDataRequest(9, { type: 'ERASURE', status: 'RESOLVED' }));
    TestBed.tick();
    backend.expectOne('/api/data-requests/erasures').flush([]);
    await fixture.whenStable();
    expect(element.textContent).toContain('No hay solicitudes de eliminación pendientes');
  });

  it('rejects an erasure with the response', async () => {
    await render([ERASURE]);
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of({ decision: 'reject', response: 'Obligación legal de conservar.' }),
    } as never);

    button('Rechazar')!.click();
    await fixture.whenStable();

    const request = backend.expectOne('/api/data-requests/erasures/9/rejection');
    expect(request.request.body).toEqual({ response: 'Obligación legal de conservar.' });
    request.flush(fakeDataRequest(9, { type: 'ERASURE', status: 'REJECTED' }));
    TestBed.tick();
    backend.expectOne('/api/data-requests/erasures').flush([]);
    await fixture.whenStable();
  });
});
