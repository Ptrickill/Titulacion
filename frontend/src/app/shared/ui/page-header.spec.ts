import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PageHeader } from './page-header';

@Component({
  imports: [PageHeader],
  template: `
    <app-page-header title="Gestión de Pacientes" subtitle="Registro y control" backLink="/physio">
      <button actions type="button">Registrar</button>
    </app-page-header>
  `,
})
class Host {}

describe('PageHeader', () => {
  it('renders title, subtitle, back link and projected actions', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toBe('Gestión de Pacientes');
    expect(element.querySelector('.subtitle')?.textContent).toBe('Registro y control');
    expect(element.querySelector('a.back')?.getAttribute('href')).toBe('/physio');
    expect(element.querySelector('.actions button')?.textContent).toBe('Registrar');
  });

  it('hides the back link and subtitle when not provided', () => {
    const fixture = TestBed.createComponent(PageHeader);
    fixture.componentRef.setInput('title', 'Inicio');
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('a.back')).toBeNull();
    expect(element.querySelector('.subtitle')).toBeNull();
  });
});
