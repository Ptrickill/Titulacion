import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { EmptyState } from './empty-state';

@Component({
  imports: [EmptyState],
  template: `
    <app-empty-state icon="search_off" title="Sin resultados" message="Pruebe otro criterio.">
      <button type="button">Limpiar filtros</button>
    </app-empty-state>
  `,
})
class Host {}

describe('EmptyState', () => {
  it('renders icon, title, message and projected action as a status region', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const element = (fixture.nativeElement as HTMLElement).querySelector('app-empty-state')!;

    expect(element.getAttribute('role')).toBe('status');
    expect(element.querySelector('mat-icon')?.textContent).toBe('search_off');
    expect(element.querySelector('h2')?.textContent).toBe('Sin resultados');
    expect(element.querySelector('.message')?.textContent).toBe('Pruebe otro criterio.');
    expect(element.querySelector('.action button')?.textContent).toBe('Limpiar filtros');
  });
});
