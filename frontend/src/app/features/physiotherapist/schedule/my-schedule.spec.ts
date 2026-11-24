import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { fakeSchedule } from '../../../shared/schedules/schedule-testing';
import { MySchedule } from './my-schedule';

describe('MySchedule (MEJ-04)', () => {
  let fixture: ComponentFixture<MySchedule>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  async function load(respond: (request: ReturnType<HttpTestingController['expectOne']>) => void) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MySchedule);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    TestBed.tick();
    respond(backend.expectOne('/api/physio/schedule'));
    await fixture.whenStable();
  }

  afterEach(() => backend.verify());

  it('shows the own weekly schedule, read only', async () => {
    await load((request) => request.flush(fakeSchedule(4)));

    expect(element.querySelector('h1')?.textContent).toContain('Mi Horario');
    expect(element.textContent).toContain('Jornada de la clínica: 07:00 a 20:00');
    expect(element.querySelector('app-weekly-schedule')?.textContent).toContain('08:00 – 13:00');
    expect(element.textContent).toContain('Total semanal: 16 h');
    expect(element.querySelector('.note')?.textContent).toContain(
      'Su horario lo registran la administración y la dirección operativa.',
    );
    // Read only: nothing to edit
    expect(element.querySelectorAll('button, input, mat-select').length).toBe(0);
  });

  it('says when the account has no staff record', async () => {
    await load((request) =>
      request.flush(
        { detail: 'Su cuenta no tiene un registro del personal.' },
        { status: 404, statusText: 'Not Found' },
      ),
    );

    expect(element.textContent).toContain('Su cuenta no tiene un registro del personal');
  });

  it('offers to retry when the schedule cannot be loaded', async () => {
    await load((request) => request.flush({}, { status: 500, statusText: 'Server Error' }));

    expect(element.textContent).toContain('No se pudo cargar su horario');
    expect(element.querySelector('button')?.textContent).toContain('Reintentar');
  });
});
