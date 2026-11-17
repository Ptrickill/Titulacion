package ec.rehabelite.reports.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * A minimal flowing layout on A4 pages with PDFBox (HU-15): paragraphs with line wrapping and tables whose rows never
 * split and whose header repeats on every page. The text uses Helvetica, a standard font that is not embedded and
 * covers Spanish (WinAnsi); a character it cannot encode is written as "?", so a name never makes the PDF fail.
 * Not thread safe: one instance per document.
 */
final class PdfLayout {

    /** Colors of the theme of the frontend (tokens --re-*), as RGB. */
    static final int NAVY = 0x0F172A;
    static final int TEAL_TEXT = 0x0F766E;
    static final int TEXT = 0x1F2937;
    static final int MUTED = 0x6B7280;
    private static final int FIELD = 0xF3F4F6;
    private static final int BORDER = 0xE5E7EB;

    static final Style TITLE = new Style(true, 18, NAVY);
    static final Style HEADING = new Style(true, 12, TEAL_TEXT);
    static final Style BODY = new Style(false, 9.5f, TEXT);
    static final Style NOTE = new Style(false, 8.5f, MUTED);
    private static final Style CELL = new Style(false, 8.5f, TEXT);
    private static final Style CELL_HEADER = new Style(true, 8.5f, NAVY);

    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float MARGIN = 48;
    private static final float FOOTER_HEIGHT = 28;
    private static final float CELL_PADDING = 4;
    private static final float LEADING = 1.3f;
    private static final char REPLACEMENT = '?';

    /** A font weight, size and color. */
    record Style(boolean bold, float size, int color) {
    }

    /**
     * A table column.
     *
     * @param weight share of the width of the page
     * @param right  whether the column is aligned to the right (numbers)
     */
    record Column(String header, float weight, boolean right) {
    }

    private final PDDocument document = new PDDocument();
    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final Map<Integer, Boolean> encodable = new HashMap<>();
    private PDPageContentStream stream;
    private float y;

    PdfLayout(String title) {
        document.getDocumentInformation().setTitle(title);
        document.getDocumentInformation().setCreator("Rehab Elite");
    }

    float contentWidth() {
        return PAGE_SIZE.getWidth() - 2 * MARGIN;
    }

    /** A paragraph wrapped to the width of the page. */
    void text(String text, Style style) {
        for (String line : wrap(text, style, contentWidth())) {
            float height = style.size() * LEADING;
            ensureSpace(height);
            write(line, style, MARGIN, y - style.size());
            y -= height;
        }
    }

    /** Vertical space; nothing at the top of a page. */
    void space(float points) {
        if (stream != null) {
            y -= points;
        }
    }

    /** A heading kept on the same page as at least its first lines of content. */
    void heading(String text) {
        ensureSpace(HEADING.size() * LEADING + 40);
        space(6);
        text(text, HEADING);
        space(2);
    }

    /**
     * A table. The header (bold, on a gray band) repeats at the top of each new page; a row is never split between
     * pages. Without headers, only the rows are drawn (label-value lists).
     */
    void table(List<Column> columns, List<List<String>> rows, boolean showHeader) {
        float total = (float) columns.stream().mapToDouble(Column::weight).sum();
        float[] widths = new float[columns.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = contentWidth() * columns.get(i).weight() / total;
        }
        List<String> headers = columns.stream().map(Column::header).toList();
        if (showHeader) {
            ensureSpace(rowHeight(headers, widths, CELL_HEADER) + rowHeight(rows.isEmpty() ? headers : rows.getFirst(),
                    widths, CELL));
            row(columns, headers, widths, CELL_HEADER, true);
        }
        for (List<String> cells : rows) {
            float height = rowHeight(cells, widths, CELL);
            if (needsPage(height)) {
                newPage();
                if (showHeader) {
                    row(columns, headers, widths, CELL_HEADER, true);
                }
            }
            row(columns, cells, widths, CELL, false);
        }
    }

    /**
     * Closes the document: writes the footer of every page (the text on the left, "Página N de M" on the right) and
     * returns the bytes of the PDF.
     */
    byte[] finish(String footer) {
        try (document) {
            ensureSpace(0);
            stream.close();
            int pages = document.getNumberOfPages();
            for (int i = 0; i < pages; i++) {
                PDPage page = document.getPage(i);
                try (PDPageContentStream footerStream = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    stream = footerStream;
                    float line = MARGIN - 8;
                    stroke(MARGIN, line + 10, PAGE_SIZE.getWidth() - MARGIN, line + 10, BORDER);
                    write(fit(footer, NOTE, contentWidth() - 80), NOTE, MARGIN, line);
                    String number = "Página " + (i + 1) + " de " + pages;
                    write(number, NOTE, PAGE_SIZE.getWidth() - MARGIN - width(number, NOTE), line);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void row(List<Column> columns, List<String> cells, float[] widths, Style style, boolean header) {
        float height = rowHeight(cells, widths, style);
        try {
            if (header) {
                stream.setNonStrokingColor(red(FIELD), green(FIELD), blue(FIELD));
                stream.addRect(MARGIN, y - height, contentWidth(), height);
                stream.fill();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        float x = MARGIN;
        for (int i = 0; i < widths.length; i++) {
            float baseline = y - CELL_PADDING - style.size();
            for (String line : wrap(cells.get(i), style, widths[i] - 2 * CELL_PADDING)) {
                float left = columns.get(i).right() ? x + widths[i] - CELL_PADDING - width(line, style)
                        : x + CELL_PADDING;
                write(line, style, left, baseline);
                baseline -= style.size() * LEADING;
            }
            x += widths[i];
        }
        y -= height;
        stroke(MARGIN, y, MARGIN + contentWidth(), y, BORDER);
    }

    private float rowHeight(List<String> cells, float[] widths, Style style) {
        int lines = 1;
        for (int i = 0; i < widths.length; i++) {
            lines = Math.max(lines, wrap(cells.get(i), style, widths[i] - 2 * CELL_PADDING).size());
        }
        return lines * style.size() * LEADING + 2 * CELL_PADDING;
    }

    private boolean needsPage(float height) {
        return stream == null || y - height < MARGIN + FOOTER_HEIGHT;
    }

    private void ensureSpace(float height) {
        if (needsPage(height)) {
            newPage();
        }
    }

    private void newPage() {
        try {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_SIZE.getHeight() - MARGIN;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(String text, Style style, float x, float baseline) {
        try {
            stream.beginText();
            stream.setFont(font(style), style.size());
            stream.setNonStrokingColor(red(style.color()), green(style.color()), blue(style.color()));
            stream.newLineAtOffset(x, baseline);
            stream.showText(text);
            stream.endText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void stroke(float x1, float y1, float x2, float y2, int color) {
        try {
            stream.setStrokingColor(red(color), green(color), blue(color));
            stream.setLineWidth(0.6f);
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
            stream.stroke();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The lines of the text within the width: by words, and a word longer than the width by characters. */
    List<String> wrap(String text, Style style, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : safe(text).split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(candidate, style) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            for (char character : word.toCharArray()) {
                if (!line.isEmpty() && width(line.toString() + character, style) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(character);
            }
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** The text cut with "..." to fit in one line. */
    private String fit(String text, Style style, float maxWidth) {
        String safe = safe(text);
        if (width(safe, style) <= maxWidth) {
            return safe;
        }
        String cut = safe;
        while (!cut.isEmpty() && width(cut + "...", style) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    /** Whitespace and control characters become one space; characters Helvetica cannot encode become "?". */
    String safe(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
                safe.append(' ');
            } else if (encodable.computeIfAbsent(codePoint, this::canEncode)) {
                safe.appendCodePoint(codePoint);
            } else {
                safe.append(REPLACEMENT);
            }
        });
        return safe.toString().trim();
    }

    private boolean canEncode(int codePoint) {
        try {
            regular.encode(new String(Character.toChars(codePoint)));
            return true;
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }

    float width(String text, Style style) {
        try {
            return font(style).getStringWidth(text) / 1000f * style.size();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PDFont font(Style style) {
        return style.bold() ? bold : regular;
    }

    private static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255f;
    }

    private static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255f;
    }

    private static float blue(int rgb) {
        return (rgb & 0xFF) / 255f;
    }

}
