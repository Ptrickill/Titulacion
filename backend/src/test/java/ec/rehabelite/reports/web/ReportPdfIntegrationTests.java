package ec.rehabelite.reports.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestClinicalText;
import ec.rehabelite.TestPatients;
import ec.rehabelite.TestSessions;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.domain.ClinicCalendar;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-15: GET /api/reports/summary.pdf. "Given a filter, the report and the export reflect the same result": the text
 * of the PDF, read back with PDFBox, has the same indicators, sessions per physiotherapist and treatments as the JSON
 * of the view for the same parameters. It has the filters, the date and time of generation and the name of the user,
 * and never the national ID.
 */
@AutoConfigureMockMvc
class ReportPdfIntegrationTests extends AbstractIntegrationTest {

    private static final String PDF = "/api/reports/summary.pdf";
    private static final String CURRENT_STATUS_NOTE = "El estado corresponde al estado actual de cada tratamiento.";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Label of each indicator in the PDF and its path in the JSON of the summary. */
    private static final Map<String, String> INDICATORS = indicators();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User director;
    private Long valeria;
    private Long mateo;

    @BeforeEach
    void setUp() {
        director = createUser(Role.OPERATIONS_DIRECTOR, "Gabriela Torres Dirección");
        valeria = createUser(Role.PHYSIOTHERAPIST, "Valeria Andrade Pdf").getId();
        mateo = createUser(Role.PHYSIOTHERAPIST, "Mateo Ríos Pdf").getId();
        Long active = treatmentOf("Rosa", "Ortiz", valeria, "ACTIVE", "2025-09-10");
        Long finished = treatmentOf("Luis", "Quishpe", valeria, "COMPLETED", "2025-08-01");
        closedAt(finished, valeria, "COMPLETED", "2025-10-20T15:00:00Z");
        Long abandoned = treatmentOf("Daniela", "Zambrano", mateo, "ABANDONED", "2025-10-03");
        closedAt(abandoned, mateo, "ABANDONED", "2025-10-28T15:00:00Z");
        session(active, valeria, 1, "2025-09-30", "COMPLETED");
        session(active, valeria, 2, "2025-10-01", "COMPLETED");
        session(active, mateo, 3, "2025-10-15", "RESCHEDULED");
        session(finished, valeria, 1, "2025-10-02", "NO_SHOW");
        session(finished, valeria, 2, "2025-10-31", "COMPLETED");
        session(abandoned, mateo, 1, "2025-10-04", "COMPLETED");
        session(abandoned, mateo, 2, "2025-11-01", "COMPLETED");
    }

    /** Every combination of the filters: the PDF has the totals of the JSON of the view. */
    @Test
    void thePdfHasTheSameTotalsAsTheViewForEveryFilter() throws Exception {
        List<Map<String, String>> filters = List.of(
                Map.of("physiotherapistId", valeria.toString()),
                Map.of("physiotherapistId", mateo.toString(), "from", "2025-10-01", "to", "2025-10-31"),
                Map.of("physiotherapistId", valeria.toString(), "status", "COMPLETED", "from", "2025-10-01"),
                Map.of("physiotherapistId", valeria.toString(), "to", "2025-10-01"),
                Map.of("status", "ABANDONED", "from", "2025-10-01", "to", "2025-10-31"),
                Map.of());
        for (Map<String, String> filter : filters) {
            Map<String, Object> summary = json(get("/api/reports/summary"), filter);
            Map<String, Object> list = json(get("/api/reports/treatments").param("size", "1"), filter);
            String pdf = pdfText(filter);

            for (Map.Entry<String, String> indicator : INDICATORS.entrySet()) {
                assertThat(valueOf(pdf, indicator.getKey())).as("%s with %s", indicator.getKey(), filter)
                        .isEqualTo(JsonPath.read(summary, indicator.getValue()).toString());
            }
            assertThat(pdf).as("average with %s", filter).containsPattern("Promedio de sesiones por fisioterapeuta\\s+"
                    + decimal(summary, "$.averageSessionsPerPhysiotherapist"));
            assertThat(pdf).as("treatments with %s", filter)
                    .contains("Tratamientos (" + JsonPath.read(list, "$.totalElements") + ")");
            List<Map<String, Object>> perPhysiotherapist = JsonPath.read(summary, "$.sessionsByPhysiotherapist");
            for (Map<String, Object> row : perPhysiotherapist) {
                assertThat(pdf).containsPattern(
                        Pattern.quote(row.get("name").toString()) + "\\s+" + row.get("sessions"));
            }
        }
    }

    @Test
    void thePdfListsEveryTreatmentOfTheFilterLikeTheViewAndNeverTheNationalId() throws Exception {
        Map<String, String> filter = Map.of("physiotherapistId", valeria.toString());
        // More than a page of the view: the PDF has them all
        for (int i = 0; i < 6; i++) {
            treatmentOf("Paciente " + i, "Informe", valeria, "ACTIVE", "2025-07-0" + (i + 1));
        }
        Map<String, Object> list = json(get("/api/reports/treatments").param("size", "50"), filter);
        String pdf = pdfText(filter);

        List<Map<String, Object>> rows = JsonPath.read(list, "$.content");
        assertThat(rows).hasSize(8);
        assertThat(pdf).contains("Tratamientos (8)");
        int previous = -1;
        for (Map<String, Object> row : rows) {
            String patient = row.get("patientName").toString();
            int position = pdf.indexOf(patient);
            assertThat(position).as("%s after the previous row", patient).isGreaterThan(previous);
            previous = position;
            assertThat(pdf).contains(JsonPath.read(row, "$.diagnosis.code").toString());
            assertThat(pdf).contains(LocalDate.parse(row.get("startDate").toString()).format(DATE));
        }
        assertThat(pdf).contains("Valeria Andrade Pdf", "Activo", "Finalizado");
        for (String nationalId : jdbc.queryForList("""
                SELECT p.national_id FROM patients p JOIN clinical_records r ON r.patient_id = p.id
                JOIN treatments t ON t.clinical_record_id = r.id JOIN responsible_assignments a ON a.treatment_id = t.id
                WHERE a.physiotherapist_id = ? AND a.valid_to IS NULL""", String.class, valeria)) {
            assertThat(pdf).doesNotContain(nationalId);
        }
    }

    @Test
    void thePdfHasTheFiltersTheCurrentStatusNoteTheUserAndTheMomentOfGeneration() throws Exception {
        String pdf = pdfText(Map.of("status", "COMPLETED", "physiotherapistId", valeria.toString(),
                "from", "2025-10-01", "to", "2025-10-31"));

        assertThat(pdf).contains("Informe operativo", "Filtros aplicados", "Estado del tratamiento", "Finalizado",
                "Valeria Andrade Pdf", "Del 01/10/2025 al 31/10/2025", CURRENT_STATUS_NOTE,
                "Indicadores", "Sesiones por fisioterapeuta");
        String today = ClinicCalendar.today(Clock.systemUTC()).format(DATE);
        assertThat(pdf).containsPattern("Generado el " + Pattern.quote(today)
                + " a las \\d{2}:\\d{2} \\(hora de Quito\\) por Gabriela Torres Dirección\\.");
        assertThat(pdf).contains("Página 1 de");
    }

    @Test
    void withoutFiltersThePdfSaysSoAndHasNoNoteOfTheCurrentStatus() throws Exception {
        String pdf = pdfText(Map.of("physiotherapistId", valeria.toString()));

        assertThat(pdf).contains("Todos los estados", "Todas las fechas").doesNotContain(CURRENT_STATUS_NOTE);
        assertThat(pdfText(Map.of())).contains("Todos los fisioterapeutas");
        assertThat(pdfText(Map.of("from", "2025-10-01"))).contains("Desde el 01/10/2025", CURRENT_STATUS_NOTE);
        assertThat(pdfText(Map.of("to", "2025-10-31"))).contains("Hasta el 31/10/2025", CURRENT_STATUS_NOTE);
    }

    @Test
    void thePdfIsADownloadThatIsNotCached() throws Exception {
        String today = ClinicCalendar.today(Clock.systemUTC()).toString();

        perform(get(PDF).accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"informe-operativo-" + today + ".pdf\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void eachPdfIsAuditedAsOneExportOfTheReportAndOneReadPerTreatment() throws Exception {
        pdfText(Map.of("physiotherapistId", valeria.toString()));

        List<Map<String, Object>> entries = jdbc.queryForList(
                "SELECT action, entity_type, entity_id FROM audit_logs WHERE user_id = ? ORDER BY id",
                director.getId());
        assertThat(entries).filteredOn(entry -> "EXPORT".equals(entry.get("action"))).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("entity_type", "Report")
                        .containsEntry("entity_id", null));
        List<Long> treatments = jdbc.queryForList("""
                SELECT treatment_id FROM responsible_assignments WHERE physiotherapist_id = ? AND valid_to IS NULL""",
                Long.class, valeria);
        assertThat(entries).filteredOn(entry -> "READ".equals(entry.get("action")))
                .allSatisfy(entry -> assertThat(entry).containsEntry("entity_type", "Treatment"))
                .extracting(entry -> ((Number) entry.get("entity_id")).longValue())
                .containsExactlyInAnyOrderElementsOf(treatments);
        assertThat(entries).hasSize(1 + treatments.size());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMINISTRATOR", "PHYSIOTHERAPIST", "PATIENT"})
    void otherRolesGet403AuditedAsAccessDenied(Role role) throws Exception {
        User other = createUser(role, "Otro rol");

        mockMvc.perform(get(PDF).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(other,
                        Instant.now()).token()))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForList("SELECT action, entity_type FROM audit_logs WHERE user_id = ?", other.getId()))
                .singleElement().satisfies(entry -> assertThat(entry)
                        .containsEntry("action", "ACCESS_DENIED")
                        .containsEntry("entity_type", "reports"));
    }

    @Test
    void thePdfIsReadOnly() throws Exception {
        perform(post(PDF)).andExpect(status().isMethodNotAllowed());
    }

    // ----- helpers -----

    private static Map<String, String> indicators() {
        Map<String, String> indicators = new LinkedHashMap<>();
        indicators.put("Tratamientos activos", "$.treatments.active");
        indicators.put("Tratamientos finalizados", "$.treatments.completed");
        indicators.put("Abandonos", "$.treatments.abandoned");
        indicators.put("Sesiones registradas", "$.sessions.total");
        indicators.put("Sesiones realizadas", "$.sessions.completed");
        indicators.put("Sesiones reprogramadas", "$.sessions.rescheduled");
        indicators.put("Sesiones con inasistencia (No asistió)", "$.sessions.noShow");
        return indicators;
    }

    /** The number after the label, on its line of the PDF. */
    private static String valueOf(String pdf, String label) {
        Matcher matcher = Pattern.compile(Pattern.quote(label) + "[ \\t]+(\\d+)").matcher(pdf);
        assertThat(matcher.find()).as("indicator %s in the PDF", label).isTrue();
        return matcher.group(1);
    }

    /** One decimal with a comma, as the PDF writes it. */
    private static String decimal(Map<String, Object> json, String path) {
        return new BigDecimal(JsonPath.read(json, path).toString()).setScale(1).toPlainString().replace('.', ',');
    }

    private Map<String, Object> json(MockHttpServletRequestBuilder request, Map<String, String> filter)
            throws Exception {
        filter.forEach(request::param);
        return JsonPath.read(perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(), "$");
    }

    private String pdfText(Map<String, String> filter) throws Exception {
        MockHttpServletRequestBuilder request = get(PDF);
        filter.forEach(request::param);
        byte[] content = perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return text(content);
    }

    private static String text(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private Long treatmentOf(String firstName, String lastName, Long responsibleId, String status, String start) {
        Long patientId = TestPatients.insert(jdbc, firstName, lastName);
        Long treatmentId = TestTreatments.insertFor(jdbc, TestTreatments.insertRecord(jdbc, patientId), status,
                LocalDate.parse(start), 7, 12);
        TestTreatments.assignResponsible(jdbc, treatmentId, responsibleId);
        return treatmentId;
    }

    private void closedAt(Long treatmentId, Long userId, String newStatus, String instant) {
        jdbc.update("""
                INSERT INTO treatment_status_changes (treatment_id, user_id, previous_status, new_status, reason,
                                                      changed_at)
                VALUES (?, ?, 'ACTIVE', ?, ?, ?)""", treatmentId, userId, newStatus,
                TestClinicalText.encrypt("Cierre de prueba"), Timestamp.from(Instant.parse(instant)));
    }

    private void session(Long treatmentId, Long responsibleId, int sequence, String date, String status) {
        TestSessions.insert(jdbc, treatmentId, responsibleId, sequence, LocalDate.parse(date), "09:00", 5, status);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(director, Instant.now()).token()));
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("reportes-pdf-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

}
