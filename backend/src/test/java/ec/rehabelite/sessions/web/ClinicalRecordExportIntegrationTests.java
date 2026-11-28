package ec.rehabelite.sessions.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestClinicalRecords;
import ec.rehabelite.TestPatients;
import ec.rehabelite.TestSessions;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MEJ-06 (D18): the physiotherapist exports the whole clinical record from the record view, PDF and JSON, with the
 * plan, the responsibles, the status changes and the sessions including their internal notes. Audited as EXPORT
 * ClinicalRecord and never cached; the reception never gets it.
 */
@AutoConfigureMockMvc
class ClinicalRecordExportIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User physio;
    private Long patientId;
    private Long recordId;

    @BeforeEach
    void setUp() throws Exception {
        physio = userRepository.save(User.create("exporta-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Lcda. Valeria Andrade", Role.PHYSIOTHERAPIST, null, Instant.now()));
        patientId = TestPatients.insert(jdbc, "Rosa Elena", "Ortiz Vega");
        perform(physio, post("/api/patients/{id}/clinical-record", patientId).contentType(MediaType.APPLICATION_JSON)
                .content(TestClinicalRecords.record("No fuma"))).andExpect(status().isCreated());
        recordId = jdbc.queryForObject("SELECT id FROM clinical_records WHERE patient_id = ?", Long.class, patientId);
        // The treatment started a month ago, so a session of yesterday is inside it
        jdbc.update("UPDATE treatments SET start_date = start_date - 30 WHERE clinical_record_id = ?", recordId);
        Long treatmentId = jdbc.queryForObject("SELECT id FROM treatments WHERE clinical_record_id = ?", Long.class,
                recordId);
        perform(physio, post("/api/treatments/{id}/therapy-sessions", treatmentId)
                .contentType(MediaType.APPLICATION_JSON).content(TestSessions.body(4)))
                .andExpect(status().isCreated());
        perform(physio, patch("/api/treatments/{id}/status", treatmentId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newStatus\":\"COMPLETED\",\"reason\":\"Alta por objetivos cumplidos\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void theJsonHasTheWholeRecordWithTheSessionsAndTheirInternalNotes() throws Exception {
        perform(physio, get("/api/patients/{id}/clinical-record/export.json", patientId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        startsWith("attachment; filename=\"historia-clinica-" + patientId)))
                .andExpect(jsonPath("$.generatedBy").value("Lcda. Valeria Andrade"))
                .andExpect(jsonPath("$.patient.medicalRecordNumber").value(TestPatients.nationalIdOf(jdbc, patientId)))
                .andExpect(jsonPath("$.patient.email").doesNotExist())
                .andExpect(jsonPath("$.clinicalRecord.habits").value("No fuma"))
                .andExpect(jsonPath("$.clinicalRecord.treatments[0].chiefComplaint")
                        .value("Dolor lumbar al levantar peso"))
                .andExpect(jsonPath("$.clinicalRecord.treatments[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.clinicalRecord.treatments[0].responsibles[0].name")
                        .value("Lcda. Valeria Andrade"))
                .andExpect(jsonPath("$.clinicalRecord.treatments[0].statusChanges[0].reason")
                        .value("Alta por objetivos cumplidos"))
                .andExpect(jsonPath("$.clinicalRecord.treatments[0].sessions[0].clinicalObservations")
                        .value("Buena tolerancia al ejercicio"))
                .andExpect(jsonPath("$.clinicalRecord.treatments[0].sessions[0].progressStatus").value("IMPROVING"));

        assertThat(exports()).isEqualTo(1);
    }

    @Test
    void thePdfHasTheSameContent() throws Exception {
        byte[] pdf = perform(physio, get("/api/patients/{id}/clinical-record/export.pdf", patientId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text).contains("Historia clínica", "Rosa Elena Ortiz Vega", "No fuma",
                    "Dolor lumbar al levantar peso", "Alta por objetivos cumplidos", "Buena tolerancia al ejercicio",
                    "Finalizado", "En mejoría");
        }
        assertThat(exports()).isEqualTo(1);
    }

    @Test
    void onlyThePhysiotherapistExportsAndAPatientWithoutRecordIsNotFound() throws Exception {
        User reception = userRepository.save(User.create("recepcion-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Recepción", Role.ADMINISTRATOR, null, Instant.now()));
        perform(reception, get("/api/patients/{id}/clinical-record/export.json", patientId))
                .andExpect(status().isForbidden());

        Long withoutRecord = TestPatients.insert(jdbc, "Sin", "Historial");
        perform(physio, get("/api/patients/{id}/clinical-record/export.pdf", withoutRecord))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El paciente no tiene historial clínico."));
        perform(physio, get("/api/patients/{id}/clinical-record/export.json", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El paciente no existe."));
        assertThat(exports()).isZero();
    }

    private int exports() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'EXPORT' AND entity_type = 'ClinicalRecord'
                AND entity_id = ? AND user_id = ?""", Integer.class, recordId, physio.getId());
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(user, Instant.now()).token()));
    }

}
