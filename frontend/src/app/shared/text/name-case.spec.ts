import { nameCase } from './name-case';

/** Same cases as the backend's NameCaseTests: both sides must capitalize alike. */
describe('nameCase', () => {
  it('removes extra spaces and capitalizes each word', () => {
    expect(nameCase('  MARÍA   josé ')).toBe('María José');
    expect(nameCase('ñusta')).toBe('Ñusta');
    expect(nameCase('PÉREZ GÓMEZ')).toBe('Pérez Gómez');
  });

  it('keeps the particles in lower case unless they start the name', () => {
    expect(nameCase('SAN MIGUEL DE LOS BANCOS')).toBe('San Miguel de los Bancos');
    expect(nameCase('pérez DE LA TORRE')).toBe('Pérez de la Torre');
    expect(nameCase('DEL POZO y ruiz')).toBe('Del Pozo y Ruiz');
    expect(nameCase('LAS NAVES')).toBe('Las Naves');
  });

  it('capitalizes after a parenthesis or a hyphen', () => {
    expect(nameCase('VICTORIA DEL PORTETE (CAB. EN IRQUIS)')).toBe(
      'Victoria del Portete (Cab. En Irquis)',
    );
    expect(nameCase('maría-josé')).toBe('María-José');
  });

  it('handles empty values', () => {
    expect(nameCase(null)).toBe('');
    expect(nameCase('   ')).toBe('');
  });
});
