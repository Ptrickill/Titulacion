import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PortalInstruction } from './portal-api';
import { fakeInstruction } from './portal-testing';
import { TherapistInstructionCard } from './therapist-instruction-card';

describe('TherapistInstructionCard', () => {
  let fixture: ComponentFixture<TherapistInstructionCard>;
  let element: HTMLElement;

  const text = (selector: string) =>
    element.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();

  function render(instruction: PortalInstruction | null): void {
    fixture = TestBed.createComponent(TherapistInstructionCard);
    fixture.componentRef.setInput('instruction', instruction);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  it('shows the last instructions with the physiotherapist who wrote them', () => {
    render(fakeInstruction());

    expect(text('.title')).toBe('Indicación del terapeuta');
    expect(text('.therapist')).toBe('Fisioterapeuta: Lcda. Valeria Andrade');
    expect(text('blockquote')).toBe(
      '“Continuar la rutina de fortalecimiento domiciliario 3 días por semana.”',
    );
    expect(text('.summary')).toContain(
      'Sigue estas pautas para consolidar la mejoría entre sesiones.',
    );
  });

  it('says in which session and on which day the instructions were written', () => {
    render(fakeInstruction({ sequenceNumber: 4, sessionDate: '2026-10-15' }));
    expect(text('figcaption')).toBe('Sesión 04 · 15 de octubre');

    fixture.componentRef.setInput(
      'instruction',
      fakeInstruction({ sequenceNumber: 12, sessionDate: '2026-11-03' }),
    );
    fixture.detectChanges();
    expect(text('figcaption')).toBe('Sesión 12 · 03 de noviembre');
  });

  it('quotes the instructions in a figure with its caption, for screen readers', () => {
    render(fakeInstruction());

    const figure = element.querySelector('figure')!;
    expect(figure.querySelector('blockquote')).not.toBeNull();
    expect(figure.querySelector('figcaption')).not.toBeNull();
  });

  it('does not name the physiotherapist when the account no longer exists', () => {
    render(fakeInstruction({ responsibleName: null }));

    expect(text('.therapist')).toBe('Indicación de tu fisioterapeuta');
    expect(text('blockquote')).toContain('Continuar la rutina');
  });

  it('says that there are no instructions yet', () => {
    render(null);

    expect(text('.title')).toBe('Indicación del terapeuta');
    expect(text('.empty')).toBe('Tu fisioterapeuta aún no ha registrado indicaciones.');
    expect(element.querySelector('blockquote')).toBeNull();
    expect(element.querySelector('.summary')).toBeNull();
  });

  it('has no control to edit anything', () => {
    render(fakeInstruction());

    expect(element.querySelectorAll('button, a, input, select, textarea')).toHaveLength(0);
  });
});
