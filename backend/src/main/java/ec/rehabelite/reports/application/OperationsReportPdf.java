package ec.rehabelite.reports.application;

import ec.rehabelite.reports.application.PdfLayout.Column;
import ec.rehabelite.reports.application.ReportService.ReportFilter;
import ec.rehabelite.reports.application.ReportService.ReportTreatment;
import ec.rehabelite.reports.domain.OperationsSummary;
import ec.rehabelite.reports.domain.OperationsSummary.PhysiotherapistSessions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The PDF of the operations report (HU-15), with the data of the view: the filters applied, the five indicators with
 * the sessions per status, the sessions per physiotherapist and every treatment of the filter. Like the view, it has
 * the name of the patient but never the national ID, the medical record number nor other personal data.
 */
@Component
class OperationsReportPdf {

    static final String TITLE = "Informe operativo";
    static final String CURRENT_STATUS_NOTE = "El estado corresponde al estado actual de cada tratamiento.";
    static final String FILTER_NOTE = "Los tratamientos se filtran por su estado, su responsable vigente y su periodo; "
            + "las sesiones, por el fisioterapeuta que las registró, el estado de su tratamiento y su fecha.";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, String> TREATMENT_STATUS = Map.of(
            "ACTIVE", "Activo", "COMPLETED", "Finalizado", "ABANDONED", "Abandonado");
    private static final String NOT_AVAILABLE = "No disponible";

    /**
     * What the PDF shows.
     *
     * @param physiotherapistName name of the physiotherapist of the filter; null without that filter or if the id
     *                            is not an account
     * @param generatedAt         date and time of generation, in Quito
     * @param generatedBy         full name of who generated it
     */
    record Content(ReportFilter filter, String physiotherapistName, OperationsSummary summary,
            List<ReportTreatment> treatments, ZonedDateTime generatedAt, String generatedBy) {
    }

    byte[] render(Content content) {
        PdfLayout pdf = new PdfLayout(TITLE + " - Rehab Elite");
        pdf.text(TITLE, PdfLayout.TITLE);
        pdf.text("Rehab Elite · Reportes de Dirección Operativa", PdfLayout.NOTE);
        pdf.space(4);
        pdf.text("Generado el " + content.generatedAt().format(DATE) + " a las " + content.generatedAt().format(TIME)
                + " (hora de Quito) por " + valueOr(content.generatedBy(), NOT_AVAILABLE) + ".", PdfLayout.BODY);

        filters(pdf, content);
        indicators(pdf, content.summary());
        sessionsByPhysiotherapist(pdf, content.summary());
        treatments(pdf, content.treatments());

        return pdf.finish("Rehab Elite · " + TITLE + " · Contiene datos clínicos confidenciales (LOPDP)");
    }

    private static void filters(PdfLayout pdf, Content content) {
        ReportFilter filter = content.filter();
        pdf.heading("Filtros aplicados");
        pdf.table(List.of(new Column("Filtro", 1, false), new Column("Valor", 2.4f, false)), List.of(
                List.of("Estado del tratamiento", filter.treatmentStatus() == null ? "Todos los estados"
                        : TREATMENT_STATUS.get(filter.treatmentStatus())),
                List.of("Fisioterapeuta", physiotherapist(filter.physiotherapistId(), content.physiotherapistName())),
                List.of("Periodo", period(filter.from(), filter.to()))), false);
        pdf.space(4);
        pdf.text(FILTER_NOTE, PdfLayout.NOTE);
        if (filter.hasRange()) {
            pdf.text(CURRENT_STATUS_NOTE, PdfLayout.NOTE);
        }
    }

    private static void indicators(PdfLayout pdf, OperationsSummary summary) {
        pdf.heading("Indicadores");
        pdf.table(List.of(new Column("Indicador", 3, false), new Column("Valor", 1, true)), List.of(
                List.of("Tratamientos activos", number(summary.treatments().active())),
                List.of("Tratamientos finalizados", number(summary.treatments().completed())),
                List.of("Abandonos", number(summary.treatments().abandoned())),
                List.of("Sesiones registradas", number(summary.sessions().total())),
                List.of("Sesiones realizadas", number(summary.sessions().completed())),
                List.of("Sesiones reprogramadas", number(summary.sessions().rescheduled())),
                List.of("Sesiones con inasistencia (No asistió)", number(summary.sessions().noShow())),
                List.of("Promedio de sesiones por fisioterapeuta",
                        decimal(summary.averageSessionsPerPhysiotherapist()))), true);
    }

    private static void sessionsByPhysiotherapist(PdfLayout pdf, OperationsSummary summary) {
        pdf.heading("Sesiones por fisioterapeuta");
        List<PhysiotherapistSessions> rows = summary.sessionsByPhysiotherapist();
        if (rows.isEmpty()) {
            pdf.text("No hay sesiones registradas con estos filtros.", PdfLayout.BODY);
            return;
        }
        pdf.table(List.of(new Column("Fisioterapeuta", 3, false), new Column("Sesiones", 1, true)),
                rows.stream()
                        .map(row -> List.of(valueOr(row.name(), NOT_AVAILABLE), number(row.sessions())))
                        .toList(), true);
        pdf.space(4);
        pdf.text("Promedio: " + decimal(summary.averageSessionsPerPhysiotherapist())
                + " sesiones por fisioterapeuta con al menos una sesión.", PdfLayout.NOTE);
    }

    private static void treatments(PdfLayout pdf, List<ReportTreatment> treatments) {
        pdf.heading("Tratamientos (" + treatments.size() + ")");
        if (treatments.isEmpty()) {
            pdf.text("No hay tratamientos que coincidan con los filtros.", PdfLayout.BODY);
            return;
        }
        pdf.table(List.of(new Column("Paciente", 2.2f, false), new Column("Diagnóstico CIE-10", 3, false),
                new Column("Responsable", 2, false), new Column("Estado", 1.2f, false),
                new Column("Inicio", 1.2f, false)),
                treatments.stream()
                        .map(row -> List.of(valueOr(row.patientName(), NOT_AVAILABLE),
                                row.treatment().diagnosisCode() + " · " + row.treatment().diagnosisDescription(),
                                valueOr(row.responsibleName(), "Sin responsable"),
                                TREATMENT_STATUS.getOrDefault(row.treatment().status(), row.treatment().status()),
                                row.treatment().startDate().format(DATE)))
                        .toList(), true);
    }

    private static String physiotherapist(Long id, String name) {
        if (id == null) {
            return "Todos los fisioterapeutas";
        }
        return name != null ? name : NOT_AVAILABLE;
    }

    static String period(LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return "Del " + from.format(DATE) + " al " + to.format(DATE);
        }
        if (from != null) {
            return "Desde el " + from.format(DATE);
        }
        if (to != null) {
            return "Hasta el " + to.format(DATE);
        }
        return "Todas las fechas";
    }

    private static String number(long value) {
        return Long.toString(value);
    }

    /** One decimal with a comma, as in es-EC. */
    private static String decimal(BigDecimal value) {
        return value.toPlainString().replace('.', ',');
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
