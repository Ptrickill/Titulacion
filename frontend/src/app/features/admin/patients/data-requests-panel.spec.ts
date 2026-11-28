import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { fakeDataRequest } from '../../../shared/legal/legal-testing';
import { DataRequest } from '../../../shared/legal/privacy-labels';
import { DataRequestsPanel } from './data-requests-panel';

describe('DataRequestsPanel (MEJ-06)', () => {
  let fixture: ComponentFixture<DataRequestsPanel>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const items = () => Array.from(element.querySelectorAll('.request'));
  const buttonIn = (item: Element, text: string) =>
    Array.from(item.querySelectorAll('button')).find((b) => b.textContent?.includes(text));

  async function render(requests: DataRequest[]): Promise<void> {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DataRequestsPanel);
    fixture.componentRef.setInput('patientId', 25);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    TestBed.tick();
    backend.expectOne('/api/patients/25/data-requests').flush(requests);
    await fixture.whenStable();
  }

  afterEach(() => backend.verify());

  it('lists the requests with their due date, the days since they arrived and an overdue warning', async () => {
    await render([
      fakeDataRequest(1, {
        type: 'RECTIFICATION',
        daysElapsed: 20,
        dueOn: '2026-11-21',
        overdue: true,
      }),
      fakeDataRequest(2, {
        type: 'ACCESS',
        status: 'RESOLVED',
        response: 'Se entregó la exportación.',
        resolvedAt: '2026-11-25T15:00:00Z',
      }),
    ]);

    const [open, closed] = items();
    expect(open.textContent).toContain('Rectificación');
    expect(open.textContent).toContain('Plazo vencido');
    expect(open.textContent).toContain('hace 20 días');
    expect(open.textContent).toContain('responder hasta el 21/11/2026');
    expect(closed.textContent).toContain('Resuelta');
    expect(closed.textContent).toContain('Se entregó la exportación.');
    expect(closed.querySelectorAll('button')).toHaveLength(0);
    expect(element.textContent).toContain('Plazo de respuesta: 15 días');
  });

  it('registers a request with the right and the day it was received', async () => {
    await render([]);
    expect(element.textContent).toContain('El paciente no tiene solicitudes registradas.');
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of({ type: 'PORTABILITY', receivedOn: '2026-11-26' }),
    } as never);

    Array.from(element.querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Registrar solicitud'))!
      .click();
    await fixture.whenStable();

    const request = backend.expectOne('/api/patients/25/data-requests');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ type: 'PORTABILITY', receivedOn: '2026-11-26' });
    request.flush(fakeDataRequest(3, { type: 'PORTABILITY' }));
    TestBed.tick();
    backend
      .expectOne('/api/patients/25/data-requests')
      .flush([fakeDataRequest(3, { type: 'PORTABILITY' })]);
    await fixture.whenStable();
    expect(items()).toHaveLength(1);
  });

  it('resolves a request with the response to the data subject', async () => {
    await render([fakeDataRequest(4)]);
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of('Se corrigió el dato.'),
    } as never);

    buttonIn(items()[0], 'Resolver')!.click();
    await fixture.whenStable();

    const request = backend.expectOne('/api/patients/25/data-requests/4');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'RESOLVED', response: 'Se corrigió el dato.' });
    request.flush(fakeDataRequest(4, { status: 'RESOLVED', response: 'Se corrigió el dato.' }));
    TestBed.tick();
    backend.expectOne('/api/patients/25/data-requests').flush([]);
    await fixture.whenStable();
  });

  it('never resolves an erasure: only the operations director accepts it', async () => {
    await render([fakeDataRequest(5, { type: 'ERASURE' })]);

    const [erasure] = items();
    expect(buttonIn(erasure, 'Resolver')).toBeUndefined();
    expect(buttonIn(erasure, 'Marcar en curso')).toBeTruthy();
    expect(buttonIn(erasure, 'Rechazar')).toBeTruthy();
    expect(erasure.textContent).toContain('La acepta la dirección operativa.');
  });
});
