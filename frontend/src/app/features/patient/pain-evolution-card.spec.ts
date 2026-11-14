import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PainEvolutionCard } from './pain-evolution-card';
import { PortalPainPoint } from './portal-api';
import { fakeCurve } from './portal-testing';

describe('PainEvolutionCard', () => {
  let fixture: ComponentFixture<PainEvolutionCard>;
  let element: HTMLElement;

  const clean = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
  const text = (selector: string) => clean(element.querySelector(selector));
  const bars = () => Array.from(element.querySelectorAll<HTMLElement>('.bar'));
  const labels = () => bars().map((bar) => clean(bar.querySelector('.label')));
  const scores = () => bars().map((bar) => clean(bar.querySelector('.score')));
  const fills = () => bars().map((bar) => bar.querySelector<HTMLElement>('.fill')!);
  const badge = () => text('app-status-badge span');
  const badgeIcon = () => text('app-status-badge mat-icon');

  /** The curve starts at EVA 7; the last score and the reduction come from the sessions. */
  function render(curve: PortalPainPoint[]): void {
    const initial = curve[0].painScore;
    const last = curve.length > 1 ? curve[curve.length - 1].painScore : null;
    fixture = TestBed.createComponent(PainEvolutionCard);
    fixture.componentRef.setInput('curve', curve);
    fixture.componentRef.setInput('initialPainScore', initial);
    fixture.componentRef.setInput('lastPainScore', last);
    fixture.componentRef.setInput('painReduction', last === null ? null : initial - last);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  it('shows one bar per point, starting with the initial evaluation', () => {
    render(fakeCurve(6, 5, 4));

    expect(text('.title')).toBe('Evolución del dolor (escala EVA)');
    expect(labels()).toEqual([
      'Evaluación inicial (01 de octubre)',
      'Sesión 01 (05 de octubre)',
      'Sesión 02 (07 de octubre)',
      'Sesión 03 (09 de octubre)',
    ]);
    expect(scores()).toEqual(['7/10 EVA', '6/10 EVA', '5/10 EVA', '4/10 EVA']);
  });

  it('draws each bar as wide as its EVA over 10, with the color of its intensity', () => {
    render(fakeCurve(5, 2, 0));

    expect(fills().map((fill) => fill.style.width)).toEqual(['70%', '50%', '20%', '0%']);
    expect(fills().map((fill) => fill.className)).toEqual([
      'fill tone-danger',
      'fill tone-warning',
      'fill tone-success',
      'fill tone-success',
    ]);
    expect(bars().map((bar) => bar.querySelector('.score')!.className)).toEqual([
      'score tone-danger',
      'score tone-warning',
      'score tone-success',
      'score tone-success',
    ]);
  });

  it('keeps the sequence number of each session, whatever its position', () => {
    const curve = fakeCurve(5, 6);
    curve[1].sequenceNumber = 3;
    curve[2].sequenceNumber = 12;

    render(curve);

    expect(labels().slice(1)).toEqual(['Sesión 03 (05 de octubre)', 'Sesión 12 (07 de octubre)']);
  });

  it('shows the reduction achieved when the pain went down', () => {
    render(fakeCurve(6, 5, 4));

    expect(badge()).toBe('Disminución de 7/10 a 4/10');
    expect(badgeIcon()).toBe('trending_down');
    expect(text('.summary span')).toBe(
      'Reducción de dolor lograda: 3 puntos en la escala visual analógica.',
    );
  });

  it('uses the singular for a reduction of one point', () => {
    render(fakeCurve(6));

    expect(text('.summary span')).toBe(
      'Reducción de dolor lograda: 1 punto en la escala visual analógica.',
    );
  });

  it('says that the pain went up, without calling it a reduction', () => {
    render(fakeCurve(8, 9));

    expect(badge()).toBe('Aumento de 7/10 a 9/10');
    expect(badgeIcon()).toBe('trending_up');
    expect(element.querySelector('app-status-badge')!.classList).toContain('badge-warning');
    expect(text('.summary span')).toBe(
      'El dolor aumentó 2 puntos respecto a la evaluación inicial.',
    );
  });

  it('says that nothing changed when the last score is the initial one', () => {
    render(fakeCurve(5, 7));

    expect(badge()).toBe('Sin cambios: 7/10');
    expect(badgeIcon()).toBe('trending_flat');
    expect(text('.summary span')).toBe('Sin cambios respecto a la evaluación inicial.');
  });

  it('shows only the initial evaluation when no session has taken place', () => {
    render(fakeCurve());

    expect(labels()).toEqual(['Evaluación inicial (01 de octubre)']);
    expect(element.querySelector('app-status-badge')).toBeNull();
    expect(text('.summary span')).toBe('Aún no hay sesiones realizadas.');
  });

  it('hides the bars from screen readers, which read the label and the score', () => {
    render(fakeCurve(6));

    expect(element.querySelector('.bars')!.tagName).toBe('UL');
    for (const bar of bars()) {
      expect(bar.querySelector('.rail')!.getAttribute('aria-hidden')).toBe('true');
    }
  });

  it('has no control to edit anything', () => {
    render(fakeCurve(6, 5));

    expect(element.querySelectorAll('button, a, input, select, textarea')).toHaveLength(0);
  });
});
