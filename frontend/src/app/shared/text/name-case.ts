const PARTICLES = new Set(['de', 'del', 'la', 'las', 'los', 'y']);

/**
 * Capitalization of names (MEJ-03), the same rule as the backend's NameCase: extra spaces removed and each word
 * with a capital initial and the rest in lower case ("MARÍA  josé" → "María José"). The particles de, del, la, las,
 * los and y stay in lower case unless they start the name ("SAN MIGUEL DE LOS BANCOS" → "San Miguel de los
 * Bancos"). A letter after "(" or "-" is also a word start.
 */
export function nameCase(text: string | null | undefined): string {
  const collapsed = (text ?? '').trim().replace(/\s+/g, ' ');
  if (!collapsed) {
    return '';
  }
  return collapsed
    .toLocaleLowerCase('es-EC')
    .split(' ')
    .map((word, index) => (index > 0 && PARTICLES.has(word) ? word : capitalize(word)))
    .join(' ');
}

function capitalize(word: string): string {
  let result = '';
  let start = true;
  for (const char of word) {
    const letter = /\p{L}/u.test(char);
    result += start && letter ? char.toLocaleUpperCase('es-EC') : char;
    if (letter) {
      start = false;
    } else if (char === '(' || char === '-') {
      start = true;
    }
  }
  return result;
}
