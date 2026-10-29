import { TestBed } from '@angular/core/testing';
import { StatCard } from './stat-card';

describe('StatCard', () => {
  it('renders label, value, hint and tone', () => {
    const fixture = TestBed.createComponent(StatCard);
    fixture.componentRef.setInput('label', 'Pacientes activos');
    fixture.componentRef.setInput('value', 6);
    fixture.componentRef.setInput('hint', 'De un total de 7');
    fixture.componentRef.setInput('tone', 'info');
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.label')?.textContent).toBe('Pacientes activos');
    expect(element.querySelector('.value')?.textContent).toBe('6');
    expect(element.querySelector('.hint')?.textContent).toBe('De un total de 7');
    expect(element.querySelector('.icon-tile')?.classList).toContain('tone-info');
  });

  it('omits the hint when it is not provided', () => {
    const fixture = TestBed.createComponent(StatCard);
    fixture.componentRef.setInput('label', 'Sesiones');
    fixture.componentRef.setInput('value', '—');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.hint')).toBeNull();
  });
});
