package ec.rehabelite.reports.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.reports.application.PdfLayout.Column;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** HU-15: the layout of the PDF wraps lines, breaks pages without splitting rows and never fails on a character. */
class PdfLayoutTests {

    private static final List<Column> COLUMNS = List.of(new Column("Paciente", 2, false),
            new Column("Sesiones", 1, true));

    @Test
    void aLongTextIsWrappedByWordsAndAWordLongerThanTheLineByCharacters() {
        PdfLayout layout = new PdfLayout("Prueba");

        List<String> lines = layout.wrap("Trastorno de disco lumbar y otros trastornos con radiculopatía",
                PdfLayout.BODY, 100);

        assertThat(lines).hasSizeGreaterThan(1)
                .allSatisfy(line -> assertThat(layout.width(line, PdfLayout.BODY)).isLessThanOrEqualTo(100));
        assertThat(String.join(" ", lines)).isEqualTo("Trastorno de disco lumbar y otros trastornos con radiculopatía");
        assertThat(layout.wrap("Electroestimulaciónneuromuscularprolongada", PdfLayout.BODY, 60))
                .hasSizeGreaterThan(1)
                .allSatisfy(line -> assertThat(layout.width(line, PdfLayout.BODY)).isLessThanOrEqualTo(60));
        assertThat(layout.wrap("", PdfLayout.BODY, 100)).containsExactly("");
        assertThat(layout.wrap(null, PdfLayout.BODY, 100)).containsExactly("");
    }

    @Test
    void spanishIsKeptAndACharacterHelveticaCannotEncodeBecomesAQuestionMark() {
        PdfLayout layout = new PdfLayout("Prueba");

        assertThat(layout.safe("María José Quishpe Tipán · Ñuñoa ¿Sí? ¡No! ü"))
                .isEqualTo("María José Quishpe Tipán · Ñuñoa ¿Sí? ¡No! ü");
        assertThat(layout.safe("Łukasz 😀 Ortiz")).isEqualTo("?ukasz ? Ortiz");
        assertThat(layout.safe("Línea uno\nLínea\tdos\r")).isEqualTo("Línea uno Línea dos");
    }

    @Test
    void aLongTableContinuesOnNewPagesWithItsHeaderAndEveryPageIsNumbered() throws IOException {
        PdfLayout layout = new PdfLayout("Prueba");
        layout.text("Título", PdfLayout.TITLE);
        List<List<String>> rows = IntStream.rangeClosed(1, 150)
                .mapToObj(i -> List.of("Paciente número " + i, Integer.toString(i)))
                .toList();

        layout.table(COLUMNS, rows, true);
        byte[] pdf = layout.finish("Pie de prueba");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pages = document.getNumberOfPages();
            assertThat(pages).isGreaterThan(1);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                assertThat(text).contains("Paciente Sesiones", "Pie de prueba", "Página " + page + " de " + pages);
            }
            stripper.setStartPage(1);
            stripper.setEndPage(pages);
            String all = stripper.getText(document);
            // Every row once, none split between pages
            IntStream.rangeClosed(1, 150).forEach(i -> assertThat(all).containsOnlyOnce("Paciente número " + i + " "));
        }
    }

    @Test
    void anEmptyDocumentStillHasOneNumberedPage() throws IOException {
        byte[] pdf = new PdfLayout("Vacío").finish("Pie");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getDocumentInformation().getTitle()).isEqualTo("Vacío");
            assertThat(new PDFTextStripper().getText(document)).contains("Página 1 de 1");
        }
    }

}
