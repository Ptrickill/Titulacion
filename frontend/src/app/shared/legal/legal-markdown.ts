/** A run of text, bold or not. */
export interface LegalInline {
  text: string;
  bold: boolean;
}

/** A block of a legal text. */
export type LegalBlock =
  | { kind: 'heading'; text: string }
  | { kind: 'paragraph'; inlines: LegalInline[] }
  | { kind: 'notice'; inlines: LegalInline[] }
  | { kind: 'list'; items: LegalInline[][] };

/**
 * Reads the small Markdown of the legal texts (MEJ-06) into blocks that the template renders as elements, never as
 * HTML: headings (`#`, `##`, `###`), paragraphs, notices (`>`), lists (`-`) and `**bold**`. Anything else is plain
 * text, so a text can never inject markup.
 */
export function parseLegalMarkdown(source: string): LegalBlock[] {
  const blocks: LegalBlock[] = [];
  let paragraph: string[] = [];
  let list: string[] = [];

  const flush = () => {
    if (paragraph.length) {
      blocks.push({ kind: 'paragraph', inlines: inlinesOf(paragraph.join(' ')) });
      paragraph = [];
    }
    if (list.length) {
      blocks.push({ kind: 'list', items: list.map(inlinesOf) });
      list = [];
    }
  };

  for (const rawLine of source.replace(/\r\n/g, '\n').split('\n')) {
    const line = rawLine.trim();
    if (!line) {
      flush();
    } else if (/^#{1,3}\s/.test(line)) {
      flush();
      blocks.push({ kind: 'heading', text: line.replace(/^#{1,3}\s+/, '') });
    } else if (line.startsWith('>')) {
      flush();
      blocks.push({ kind: 'notice', inlines: inlinesOf(line.replace(/^>\s*/, '')) });
    } else if (/^[-*]\s/.test(line)) {
      if (paragraph.length) {
        flush();
      }
      list.push(line.replace(/^[-*]\s+/, ''));
    } else {
      if (list.length) {
        flush();
      }
      paragraph.push(line);
    }
  }
  flush();
  return blocks;
}

function inlinesOf(text: string): LegalInline[] {
  return text
    .split(/(\*\*[^*]+\*\*)/)
    .filter((part) => part.length > 0)
    .map((part) =>
      part.startsWith('**') && part.endsWith('**') && part.length > 4
        ? { text: part.slice(2, -2), bold: true }
        : { text: part, bold: false },
    );
}
