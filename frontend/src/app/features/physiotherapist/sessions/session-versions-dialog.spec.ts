import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { SessionVersionsDialog, SessionVersionsDialogData } from './session-versions-dialog';
import { fakeVersions } from './session-testing';

describe('SessionVersionsDialog', () => {
  let fixture: ComponentFixture<SessionVersionsDialog>;
  let element: HTMLElement;
  let backend: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MAT_DIALOG_DATA,
          useValue: { sessionId: 90, sequenceNumber: 1 } satisfies SessionVersionsDialogData,
        },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SessionVersionsDialog);
    element = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('shows every version, most recent first, with its author, date and content', async () => {
    backend.expectOne('/api/therapy-sessions/90/versions').flush(fakeVersions());
    await fixture.whenStable();

    const items = Array.from(element.querySelectorAll('li')).map((li) =>
      li.textContent!.replace(/\s+/g, ' '),
    );
    expect(element.querySelector('h2')?.textContent).toContain('Versiones de la sesión N.º 1');
    expect(items).toHaveLength(2);
    expect(items[0]).toContain('Corrección');
    expect(items[0]).toContain('Vigente');
    expect(items[0]).toContain('Lcda. Valeria Andrade');
    expect(items[0]).toContain('3/10');
    expect(items[0]).toContain('Ejercicios de Williams');
    expect(items[1]).toContain('Registro');
    expect(items[1]).not.toContain('Vigente');
    expect(items[1]).toContain('04/11/2026');
    expect(items[1]).toContain('4/10');
    expect(items[1]).toContain('Movilización lumbar y TENS 15 minutos');
  });

  it('tells when the versions cannot be loaded', async () => {
    backend
      .expectOne('/api/therapy-sessions/90/versions')
      .flush({ detail: 'La sesión no existe.' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('La sesión no existe.');
  });
});
