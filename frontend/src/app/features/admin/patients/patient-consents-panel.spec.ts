import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import {
  FAKE_CONSENT_TEXTS,
  FAKE_VERSION,
  fakeConsent,
  fakeConsents,
} from '../../../shared/legal/legal-testing';
import { PatientConsentView } from '../../../shared/legal/legal-api';
import { PatientConsentsPanel } from './patient-consents-panel';

describe('PatientConsentsPanel (MEJ-06)', () => {
  let fixture: ComponentFixture<PatientConsentsPanel>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const consents = () =>
    Array.from(element.querySelectorAll('.consent')).map((item) => item.textContent ?? '');
  const button = (text: string) =>
    Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes(text));

  async function render(list: PatientConsentView[], blocked = false): Promise<void> {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PatientConsentsPanel);
    fixture.componentRef.setInput('patientId', 25);
    fixture.componentRef.setInput('patientName', 'Rosa Ortiz');
    fixture.componentRef.setInput('blocked', blocked);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    TestBed.tick();
    backend.expectOne('/api/patients/25/consents').flush({ consents: list, history: [] });
    backend.expectOne('/api/legal/consents').flush(FAKE_CONSENT_TEXTS);
    await fixture.whenStable();
  }

  afterEach(() => backend.verify());

  it('shows each consent with its state, version and channel', async () => {
    await render(
      fakeConsents(
        { status: 'PREVIOUS_VERSION', textVersion: '0.0-anterior' },
        { status: 'REVOKED', revokedAt: '2026-11-22T15:00:00Z', revocationChannel: 'PORTAL' },
      ),
    );

    const [data, email] = consents();
    expect(data).toContain('Tratamiento de datos personales y de salud');
    expect(data).toContain('Versión anterior');
    expect(data).toContain('Versión 0.0-anterior · firma física');
    expect(data).toContain(`Hay una versión nueva del texto (${FAKE_VERSION})`);
    expect(email).toContain('Revocado');
    expect(email).toContain('(portal del paciente)');
  });

  it('records a signed consent after showing its text in force', async () => {
    await render(
      fakeConsents({ status: 'PENDING', textVersion: null, channel: null, grantedAt: null }),
    );
    expect(consents()[0]).toContain(
      'Sin este consentimiento el paciente no recibe atenciones nuevas',
    );
    const dialog = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValue({ afterClosed: () => of(true) } as never);
    const changed = vi.fn();
    fixture.componentInstance.changed.subscribe(changed);

    element.querySelectorAll<HTMLButtonElement>('.consent')[0].querySelector('button')!.click();
    await fixture.whenStable();

    expect(dialog).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        data: expect.objectContaining({
          text: FAKE_CONSENT_TEXTS[0],
          statement: 'El paciente leyó y firmó este texto',
        }),
      }),
    );
    const request = backend.expectOne('/api/patients/25/consents');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ type: 'DATA_PROCESSING', version: FAKE_VERSION });
    request.flush({ consents: fakeConsents(), history: [] });
    await fixture.whenStable();

    expect(consents()[0]).toContain('Vigente');
    expect(changed).toHaveBeenCalled();
  });

  it('warns that revoking the data consent closes the portal and blocks new care', async () => {
    await render(fakeConsents());
    const confirm = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValue({ afterClosed: () => of(true) } as never);

    button('Revocar')!.click();
    await fixture.whenStable();

    expect(confirm).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        data: expect.objectContaining({
          message: expect.stringContaining('Su acceso al portal se desactivará'),
          tone: 'danger',
        }),
      }),
    );
    backend.expectOne('/api/patients/25/consents/DATA_PROCESSING/revocation').flush({
      consents: fakeConsents({
        status: 'REVOKED',
        revokedAt: '2026-11-26T15:00:00Z',
        revocationChannel: 'PHYSICAL_SIGNATURE',
      }),
      history: [],
    });
    await fixture.whenStable();
    expect(consents()[0]).toContain('Revocado');
  });

  it('offers no action on a blocked patient', async () => {
    await render(
      [
        fakeConsent('DATA_PROCESSING', {
          status: 'REVOKED',
          revokedAt: '2026-11-26T15:00:00Z',
          revocationChannel: 'ERASURE',
        }),
        fakeConsent('EMAIL_COMMUNICATIONS', {
          status: 'REVOKED',
          revokedAt: '2026-11-26T15:00:00Z',
          revocationChannel: 'ERASURE',
        }),
      ],
      true,
    );

    expect(element.querySelectorAll('button')).toHaveLength(0);
    expect(consents()[0]).toContain('(solicitud de eliminación)');
  });
});
