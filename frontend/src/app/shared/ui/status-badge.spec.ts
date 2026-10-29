import { TestBed } from '@angular/core/testing';
import { StatusBadge } from './status-badge';

describe('StatusBadge', () => {
  function render(inputs: Record<string, unknown>): HTMLElement {
    const fixture = TestBed.createComponent(StatusBadge);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the label with the neutral tone by default', () => {
    const element = render({ label: 'Inactivo' });

    expect(element.textContent?.trim()).toBe('Inactivo');
    expect(element.classList).toContain('badge-neutral');
  });

  it('applies the tone, uppercase style and icon', () => {
    const element = render({ label: 'Realizada', tone: 'success', uppercase: true, icon: 'check' });

    expect(element.classList).toContain('badge-success');
    expect(element.classList).toContain('badge-upper');
    expect(element.querySelector('mat-icon')?.textContent).toBe('check');
  });
});
