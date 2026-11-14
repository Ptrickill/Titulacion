import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TreatmentProgressCard } from './treatment-progress-card';

describe('TreatmentProgressCard', () => {
  let fixture: ComponentFixture<TreatmentProgressCard>;
  let element: HTMLElement;

  const text = (selector: string) =>
    element.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();
  const ring = () => element.querySelector<SVGCircleElement>('circle.value')!;
  /** Share of the ring that is filled, from its dash offset. */
  const filled = () => {
    const length = Number(ring().getAttribute('stroke-dasharray'));
    return 1 - Number(ring().getAttribute('stroke-dashoffset')) / length;
  };

  function render(completed: number, planned: number, percentage: number): void {
    fixture = TestBed.createComponent(TreatmentProgressCard);
    fixture.componentRef.setInput('completed', completed);
    fixture.componentRef.setInput('planned', planned);
    fixture.componentRef.setInput('percentage', percentage);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  it('shows the sessions that took place against the planned ones and the percentage', () => {
    render(4, 12, 33);

    expect(text('.title')).toBe('Progreso del tratamiento');
    expect(text('.completed')).toBe('4');
    expect(text('.planned')).toBe('de 12 sesiones');
    expect(text('.summary')).toBe('¡Completado un 33% de tu plan!');
  });

  it('fills the ring in proportion to the percentage', () => {
    render(4, 12, 33);
    expect(filled()).toBeCloseTo(0.33, 5);

    fixture.componentRef.setInput('percentage', 75);
    fixture.detectChanges();
    expect(filled()).toBeCloseTo(0.75, 5);
  });

  it('describes the ring for screen readers', () => {
    render(4, 12, 33);

    const figure = element.querySelector('.ring')!;
    expect(figure.getAttribute('role')).toBe('img');
    expect(figure.getAttribute('aria-label')).toBe('4 de 12 sesiones realizadas, 33 % del plan');
  });

  it('leaves the ring empty and invites to start when no session has taken place', () => {
    render(0, 12, 0);

    expect(filled()).toBe(0);
    expect(text('.completed')).toBe('0');
    expect(text('.summary')).toBe('Tu plan comienza con la primera sesión.');
  });

  it('shows a full ring when the plan is complete, also with more sessions than planned', () => {
    render(14, 12, 100);

    expect(filled()).toBe(1);
    expect(text('.completed')).toBe('14');
    expect(text('.planned')).toBe('de 12 sesiones');
    expect(text('.summary')).toBe('¡Completaste tu plan de sesiones!');
  });

  it('uses the singular for a plan of one session', () => {
    render(0, 1, 0);

    expect(text('.planned')).toBe('de 1 sesión');
  });

  it('has no control to edit anything', () => {
    render(4, 12, 33);

    expect(element.querySelectorAll('button, a, input, select, textarea')).toHaveLength(0);
  });
});
