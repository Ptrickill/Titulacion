import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormSection } from './form-section';

@Component({
  imports: [FormSection],
  template: `
    <app-form-section [step]="2" title="Datos personales" description="Campos obligatorios con *">
      <label class="full">Nombres <input /></label>
    </app-form-section>
  `,
})
class Host {}

describe('FormSection', () => {
  it('renders the numbered title, description and projected fields in the grid', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h2')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      '2. Datos personales',
    );
    expect(element.querySelector('.description')?.textContent).toBe('Campos obligatorios con *');
    expect(element.querySelector('.grid label.full input')).not.toBeNull();
  });
});
