import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { fakeLegalText } from '../../shared/legal/legal-testing';
import { PrivacyNoticePage } from './privacy-notice-page';

/** MEJ-06: the public page of the privacy notice in force. */
describe('PrivacyNoticePage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('shows the notice in force with its version and links back to the login without a session', async () => {
    const fixture = TestBed.createComponent(PrivacyNoticePage);
    fixture.detectChanges();
    TestBed.tick();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/legal/privacy-notice')
      .flush(fakeLegalText('privacy-notice', 'Aviso de privacidad'));
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('h1')?.textContent).toBe('Aviso de privacidad');
    expect(element.querySelector('.notice')?.textContent).toContain('BORRADOR.');
    expect(element.querySelector('h3')?.textContent).toBe('Finalidad');
    expect(element.querySelectorAll('li')).toHaveLength(2);
    expect(element.textContent).toContain('Versión 0.1-borrador');
    expect(element.querySelector('a.back')?.getAttribute('href')).toBe('/login');
  });

  it('offers to retry when the notice cannot be loaded', async () => {
    const fixture = TestBed.createComponent(PrivacyNoticePage);
    fixture.detectChanges();
    TestBed.tick();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/legal/privacy-notice')
      .flush(null, { status: 500, statusText: 'Error' });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'No se pudo cargar el aviso de privacidad.',
    );
  });
});
