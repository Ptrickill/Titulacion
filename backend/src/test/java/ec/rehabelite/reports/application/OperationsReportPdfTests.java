package ec.rehabelite.reports.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.reports.application.ReportService.ReportFilter;
import ec.rehabelite.reports.application.ReportService.ReportTreatment;
import ec.rehabelite.reports.domain.OperationsSummary;
import ec.rehabelite.sessions.SessionCount;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** HU-15: what the PDF of the operations report says. */
class OperationsReportPdfTests {

    private static final ZonedDateTime GENERATED_AT =
            ZonedDateTime.parse("2026-11-16T14:35:00-05:00[America/Guayaquil]");
    private static final OperationsSummary SUMMARY = OperationsSummary.of(
            Map.of("ACTIVE", 5L, "COMPLETED", 1L, "ABANDONED", 1L),
            List.of(new SessionCount(3L, "COMPLETED", 12), new SessionCount(3L, "RESCHEDULED", 2),
                    new SessionCount(4L, "NO_SHOW", 1)),
            Map.of(3L, "Valeria Andrade", 4L, "Mateo Ríos"));

    private final OperationsReportPdf pdf = new OperationsReportPdf();

    @Test
    void thePdfHasTheFiltersTheIndicatorsTheSessionsPerPhysiotherapistAndTheTreatments() throws IOException {
        String text = render(new ReportFilter("ACTIVE", 3L, LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-31")), "Valeria Andrade", List.of(
                treatment(30L, "Rosa Elena Ortiz", "Valeria Andrade", "ACTIVE", "2026-10-03"),
                treatment(20L, "Luis Quishpe", null, "COMPLETED", "2026-09-01")));

        assertThat(text).contains("Informe operativo",
                "Generado el 16/11/2026 a las 14:35 (hora de Quito) por Dirección Operativa.",
                "Estado del tratamiento Activo", "Fisioterapeuta Valeria Andrade",
                "Periodo Del 01/10/2026 al 31/10/2026", OperationsReportPdf.CURRENT_STATUS_NOTE);
        assertThat(text).contains("Tratamientos activos 5", "Tratamientos finalizados 1", "Abandonos 1",
                "Sesiones registradas 15", "Sesiones realizadas 12", "Sesiones reprogramadas 2",
                "Sesiones con inasistencia (No asistió) 1", "Promedio de sesiones por fisioterapeuta 7,5");
        assertThat(text).contains("Valeria Andrade 14", "Mateo Ríos 1", "Promedio: 7,5 sesiones por fisioterapeuta");
        assertThat(text).contains("Tratamientos (2)", "Rosa Elena Ortiz", "M54.5", "03/10/2026", "Activo",
                "Luis Quishpe", "Sin responsable", "Finalizado", "01/09/2026", "Página 1 de 1");
        assertThat(text.indexOf("Rosa Elena Ortiz")).isLessThan(text.indexOf("Luis Quishpe"));
    }

    @Test
    void withoutFiltersItSaysSoAndHasNoNoteOfTheCurrentStatus() throws IOException {
        String text = render(new ReportFilter(null, null, null, null), null, List.of());

        assertThat(text).contains("Estado del tratamiento Todos los estados",
                "Fisioterapeuta Todos los fisioterapeutas", "Periodo Todas las fechas",
                "Tratamientos (0)", "No hay tratamientos que coincidan con los filtros.");
        assertThat(text).doesNotContain(OperationsReportPdf.CURRENT_STATUS_NOTE);
    }

    @Test
    void eachBoundOfTheRangeIsNamedAndBringsTheNote() throws IOException {
        assertThat(OperationsReportPdf.period(LocalDate.parse("2026-10-01"), null)).isEqualTo("Desde el 01/10/2026");
        assertThat(OperationsReportPdf.period(null, LocalDate.parse("2026-10-31"))).isEqualTo("Hasta el 31/10/2026");
        assertThat(render(new ReportFilter(null, null, null, LocalDate.parse("2026-10-31")), null, List.of()))
                .contains(OperationsReportPdf.CURRENT_STATUS_NOTE);
    }

    @Test
    void withoutSessionsTheTableOfPhysiotherapistsSaysSo() throws IOException {
        OperationsSummary empty = OperationsSummary.of(Map.of(), List.of(), Map.of());

        String text = text(pdf.render(new OperationsReportPdf.Content(new ReportFilter(null, 99L, null, null), null,
                empty, List.of(), GENERATED_AT, "Dirección Operativa")));

        assertThat(text).contains("Fisioterapeuta No disponible", "No hay sesiones registradas con estos filtros.",
                "Promedio de sesiones por fisioterapeuta 0,0");
    }

    @Test
    void everyTreatmentIsInThePdfOverSeveralPages() throws IOException {
        List<ReportTreatment> many = IntStream.rangeClosed(1, 120)
                .mapToObj(i -> treatment((long) i, "Paciente " + i, "Valeria Andrade", "ACTIVE", "2026-10-01"))
                .toList();

        try (PDDocument document = Loader.loadPDF(pdf.render(new OperationsReportPdf.Content(
                new ReportFilter(null, null, null, null), null, SUMMARY, many, GENERATED_AT, "Dirección Operativa")))) {
            assertThat(document.getNumberOfPages()).isGreaterThan(2);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Tratamientos (120)");
            IntStream.rangeClosed(1, 120).forEach(i -> assertThat(text).contains("Paciente " + i + " "));
        }
    }

    private String render(ReportFilter filter, String physiotherapistName, List<ReportTreatment> treatments)
            throws IOException {
        return text(pdf.render(new OperationsReportPdf.Content(filter, physiotherapistName, SUMMARY, treatments,
                GENERATED_AT, "Dirección Operativa")));
    }

    private static String text(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private static ReportTreatment treatment(Long id, String patient, String responsible, String status,
            String start) {
        return new ReportTreatment(new PatientTreatment(id, 100 + id, status, "ACTIVE".equals(status),
                LocalDate.parse(start), 7, 12, "M54.5", "Lumbago no especificado", responsible == null ? null : 3L),
                patient, responsible);
    }

}
