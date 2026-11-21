import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PhysiotherapistSessions } from './report-api';
import { fakeSummary } from './report-testing';
import { initialsOf, SessionsByPhysiotherapistCard } from './sessions-by-physiotherapist-card';

describe('SessionsByPhysiotherapistCard', () => {
  let fixture: ComponentFixture<SessionsByPhysiotherapistCard>;
  let element: HTMLElement;

  const clean = (node: Element | null | undefined) =>
    node?.textContent?.replace(/\s+/g, ' ').trim();
  const items = () => Array.from(element.querySelectorAll<HTMLElement>('li.item'));
  const bars = () => items().map((item) => item.querySelector<HTMLElement>('.bar')!.style.width);

  function render(sessions: PhysiotherapistSessions[], average: number): void {
    fixture = TestBed.createComponent(SessionsByPhysiotherapistCard);
    fixture.componentRef.setInput('sessions', sessions);
    fixture.componentRef.setInput('averageSessions', average);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  it('has the title and the average with one decimal', () => {
    render(fakeSummary().sessionsByPhysiotherapist, 5.6);

    expect(clean(element.querySelector('.title'))).toBe('Sesiones por fisioterapeuta');
    expect(clean(element.querySelector('.subtitle'))).toBe(
      'Sesiones registradas por cada profesional',
    );
    expect(clean(element.querySelector('.average'))).toBe('Promedio: 5,6 ses/esp');
  });

  it('lists each physiotherapist in the order received, with the initials and the sessions', () => {
    render(fakeSummary().sessionsByPhysiotherapist, 5.6);

    expect(items().map((item) => clean(item.querySelector('.name')))).toEqual([
      'Lcda. Valeria Andrade',
      'Lcdo. Mateo Ríos',
      'Lcdo. Ricardo Silva',
      'Lcdo. Julián Rivas',
      'Lcda. Paula Mena',
    ]);
    expect(items().map((item) => clean(item.querySelector('.count')))).toEqual([
      '12 sesiones',
      '6 sesiones',
      '5 sesiones',
      '3 sesiones',
      '2 sesiones',
    ]);
    expect(clean(items()[0].querySelector('.avatar'))).toBe('VA');
  });

  it('draws each bar against the physiotherapist with the most sessions', () => {
    render(fakeSummary().sessionsByPhysiotherapist, 5.6);

    expect(bars()).toEqual(['100%', '50%', '42%', '25%', '17%']);
    // The bars are decoration: the number is in the text
    expect(items()[0].querySelector('.track')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('says "sesión" for one and "No disponible" when the account no longer exists', () => {
    render([{ physiotherapistId: 9, name: null, sessions: 1 }], 1);

    expect(clean(items()[0].querySelector('.count'))).toBe('1 sesión');
    expect(clean(items()[0].querySelector('.name'))).toBe('No disponible');
    expect(clean(items()[0].querySelector('.avatar'))).toBe('?');
    expect(bars()).toEqual(['100%']);
  });

  it('shows a message instead of the list when there are no sessions', () => {
    render([], 0);

    expect(items()).toHaveLength(0);
    expect(clean(element.querySelector('.empty'))).toBe(
      'No hay sesiones registradas con estos filtros.',
    );
    expect(clean(element.querySelector('.average'))).toBe('Promedio: 0,0 ses/esp');
  });

  it('takes the initials of the first two words that are not a title', () => {
    expect(initialsOf('Lcda. Valeria Andrade Mora')).toBe('VA');
    expect(initialsOf('Dr. Juan')).toBe('J');
    expect(initialsOf('mateo ríos')).toBe('MR');
  });
});
