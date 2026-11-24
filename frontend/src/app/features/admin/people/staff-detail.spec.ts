import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { fakeUser } from '../users/user-testing';
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
    await settle();
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
    await settle();

    expect(element.querySelector('.completion-notice')).toBeNull();
    expect(element.textContent).toContain('1702000165');
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
