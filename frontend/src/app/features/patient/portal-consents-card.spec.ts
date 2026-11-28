import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { PatientConsentView } from '../../shared/legal/legal-api';
import { FAKE_CONSENT_TEXTS, FAKE_VERSION, fakeConsents } from '../../shared/legal/legal-testing';
import { PortalConsentsCard } from './portal-consents-card';

/** MEJ-06: the patient sees, accepts and revokes its own consents; never an id of the patient. */
describe('PortalConsentsCard', () => {
  let fixture: ComponentFixture<PortalConsentsCard>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  const consents = () => Array.from(element.querySelectorAll('.consent'));
  const buttonIn = (item: Element, text: string) =>
    Array.from(item.querySelectorAll('button')).find((b) => b.textContent?.includes(text));

  async function render(list: PatientConsentView[]): Promise<void> {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PortalConsentsCard);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    TestBed.tick();
    backend.expectOne('/api/portal/consents').flush(list);
    backend.expectOne('/api/legal/consents').flush(FAKE_CONSENT_TEXTS);
    await fixture.whenStable();
  }

  afterEach(() => backend.verify());

  it('tells how to revoke the data consent and links to the privacy notice', async () => {
    await render(fakeConsents());

    const [data] = consents();
    expect(data.textContent).toContain('Vigente');
    expect(data.textContent).toContain('Para revocarlo, solicítalo en la recepción de la clínica.');
    expect(data.querySelectorAll('button')).toHaveLength(0);
    expect(element.querySelector('a.privacy')?.getAttribute('href')).toBe('/privacy-notice');
  });

  it('accepts the email communications after reading their text', async () => {
    await render(fakeConsents());
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(true),
    } as never);

    buttonIn(consents()[1], 'Aceptar')!.click();
    await fixture.whenStable();

    const request = backend.expectOne('/api/portal/consents');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ type: 'EMAIL_COMMUNICATIONS', version: FAKE_VERSION });
    request.flush(fakeConsents({}, {}));
    await fixture.whenStable();
    expect(consents()[1].textContent).toContain('Vigente');
    expect(buttonIn(consents()[1], 'Revocar')).toBeTruthy();
  });

  it('accepts the new version of a text and revokes the email communications', async () => {
    await render(fakeConsents({ status: 'PREVIOUS_VERSION', textVersion: '0.0-anterior' }, {}));
    expect(buttonIn(consents()[0], 'Aceptar la versión vigente')).toBeTruthy();
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(true),
    } as never);

    buttonIn(consents()[1], 'Revocar')!.click();
    await fixture.whenStable();

    backend
      .expectOne('/api/portal/consents/EMAIL_COMMUNICATIONS/revocation')
      .flush(fakeConsents({}, { status: 'REVOKED', revokedAt: '2026-11-26T15:00:00Z' }));
    await fixture.whenStable();
    expect(consents()[1].textContent).toContain('Revocado');
  });
});
