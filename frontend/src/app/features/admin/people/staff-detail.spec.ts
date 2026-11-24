import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { logInForTest } from '../../../core/auth/auth-testing';
import { fakeSchedule } from '../../../shared/schedules/schedule-testing';
import { fakeUser } from './user-testing';
import { fakeAccount, fakeStaff, legacyStaff } from './people-testing';
import { StaffDetail } from './staff-detail';
import { Staff } from './staff-api';

describe('StaffDetail (MEJ-02)', () => {
  let harness: RouterTestingHarness;
  let backend: HttpTestingController;
  let element: HTMLElement;

  async function loaded(staff: Staff): Promise<void> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/admin/people/staff/${staff.id}`, StaffDetail);
    element = harness.routeNativeElement as HTMLElement;
    backend.expectOne(`/api/staff/${staff.id}`).flush(staff);
    // MEJ-04: a physiotherapist's record also loads its schedule. A pending rxResource never lets the page become
    // stable, so the request is sent (tick) and answered before waiting
    harness.detectChanges();
    flushSchedule(staff.id);
    await settle();
  }

  function flushSchedule(staffId: number): void {
    TestBed.tick();
    backend
      .match(`/api/staff/${staffId}/schedule`)
      .forEach((request) => request.flush(fakeSchedule(staffId)));
  }

  async function settle(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  const button = (text: string) =>
    Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes(text));

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'admin/people/staff/:id', component: StaffDetail }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('shows the personal data, the role and the account of the staff member', async () => {
    await loaded(fakeStaff(4));

    expect(element.querySelector('h1')?.textContent).toContain('Valeria Andrade Mora');
    const text = element.textContent!;
    expect(text).toContain('Personal · Fisioterapeuta');
    expect(text).toContain('1702000165');
    expect(text).toContain('14/03/1990');
    expect(text).toContain('Femenino');
    expect(text).toContain('Av. Ilaló N12-34 y Río Pita, Conocoto');
    expect(element.querySelector('app-person-account-card')?.textContent).toContain(
      'Cuenta de acceso',
    );
    expect(element.querySelector('app-person-account-card')?.textContent).toContain('Activa');
    expect(button('Crear acceso al portal')).toBeUndefined();
    expect(element.querySelector('.completion-notice')).toBeNull();
  });

  it('never deactivates the account of the logged-in administrator', async () => {
    logInForTest('ADMINISTRATOR', 'Valeria Andrade Mora', 12);
    await loaded(fakeStaff(4, { role: 'ADMINISTRATOR' }));

    expect(button('Desactivar cuenta')?.disabled).toBe(true);
    expect(button('Desactivar cuenta')?.getAttribute('title')).toBe(
      'No puede desactivar su propia cuenta',
    );
  });

  it('shows the data to complete of a record created for an existing account', async () => {
    await loaded(legacyStaff(5));

    expect(element.querySelector('.completion-notice')?.textContent).toContain(
      'Datos por completar',
    );
    expect(element.textContent).toContain('Por completar');
    expect(element.textContent).toContain('Dirección incompleta');
  });

  it('resends the invitation of a pending account', async () => {
    await loaded(fakeStaff(4, { account: fakeAccount({ userId: 12 }) }));
    const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');

    button('Reenviar invitación')!.click();
    backend
      .expectOne('/api/users/12/resend-invitation')
      .flush(
        fakeUser(12, 'Valeria Andrade Mora', 'PHYSIOTHERAPIST', { status: 'PENDING_ACTIVATION' }),
      );
    await settle();

    expect(snackBar).toHaveBeenCalledWith(
      'Enviamos un nuevo enlace de activación a valeria.andrade@rehabelite.test',
      'Cerrar',
      { duration: 6000 },
    );
  });

  it('edits the record and shows the saved data', async () => {
    await loaded(legacyStaff(5));
    const saved = fakeStaff(5, { firstName: 'Paula', lastName: 'Herrera Vásconez' });
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of({ type: 'STAFF', staff: saved }),
    } as never);

    button('Editar datos')!.click();
    harness.detectChanges();
    flushSchedule(5);
    await settle();

    expect(element.querySelector('.completion-notice')).toBeNull();
    expect(element.textContent).toContain('1702000165');
  });

  describe('schedule (MEJ-04)', () => {
    it('shows the weekly schedule of a physiotherapist', async () => {
      await loaded(fakeStaff(4));

      const section = element.querySelector('app-staff-schedule-panel')!;
      expect(element.textContent).toContain('Horario');
      expect(section.textContent).toContain('Jornada de la clínica: 07:00 a 20:00');
      expect(section.textContent).toContain('08:00 – 13:00');
      expect(section.textContent).toContain('Sin atención');
      expect(section.textContent).toContain('Total semanal: 16 h');
    });

    it('edits the schedule and shows the saved week', async () => {
      await loaded(fakeStaff(4));
      const saved = fakeSchedule(4, {
        slots: [{ day: 'SATURDAY', start: '08:00', end: '12:00' }],
      });
      const open = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
        afterClosed: () => of(saved),
      } as never);
      const snackBar = vi.spyOn(TestBed.inject(MatSnackBar), 'open');

      button('Editar horario')!.click();
      await settle();

      expect(open).toHaveBeenCalled();
      const section = element.querySelector('app-staff-schedule-panel')!;
      expect(section.textContent).toContain('Total semanal: 4 h');
      expect(snackBar).toHaveBeenCalledWith('Horario guardado correctamente.', 'Cerrar', {
        duration: 5000,
      });
    });

    it('has no schedule for the administration nor the operations director', async () => {
      await loaded(fakeStaff(4, { role: 'OPERATIONS_DIRECTOR' }));

      expect(element.querySelector('app-staff-schedule-panel')).toBeNull();
      backend.expectNone('/api/staff/4/schedule');
    });
  });

  it('tells when the record does not exist', async () => {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/admin/people/staff/999', StaffDetail);
    element = harness.routeNativeElement as HTMLElement;
    backend
      .expectOne('/api/staff/999')
      .flush(
        { detail: 'El registro del personal no existe.' },
        { status: 404, statusText: 'Not Found' },
      );
    await settle();

    expect(element.textContent).toContain('El registro no existe');
  });
});
