import { parseLegalMarkdown } from './legal-markdown';

/** MEJ-06: the small Markdown of the legal texts becomes blocks; nothing is ever HTML. */
describe('parseLegalMarkdown', () => {
  it('reads headings, notices, paragraphs, lists and bold text', () => {
    const blocks = parseLegalMarkdown(
      '> **BORRADOR.** Texto de ejemplo.\n\n## Finalidad\n\nLos datos se tratan\npara la atención.\n\n- Uno\n- **Dos**\n',
    );

    expect(blocks).toEqual([
      {
        kind: 'notice',
        inlines: [
          { text: 'BORRADOR.', bold: true },
          { text: ' Texto de ejemplo.', bold: false },
        ],
      },
      { kind: 'heading', text: 'Finalidad' },
      {
        kind: 'paragraph',
        inlines: [{ text: 'Los datos se tratan para la atención.', bold: false }],
      },
      {
        kind: 'list',
        items: [[{ text: 'Uno', bold: false }], [{ text: 'Dos', bold: true }]],
      },
    ]);
  });

  it('keeps any markup as plain text', () => {
    expect(parseLegalMarkdown('<script>alert(1)</script>')).toEqual([
      { kind: 'paragraph', inlines: [{ text: '<script>alert(1)</script>', bold: false }] },
    ]);
  });
});
