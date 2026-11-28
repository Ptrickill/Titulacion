package ec.rehabelite.patients.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestClinicalRecords;
import ec.rehabelite.TestPatients;
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
 * MEJ-06 (B3, D18): the reception exports the personal data of a patient, its consents and its requests as PDF and
 * JSON, never clinical data; each download is audited as EXPORT Patient and is not cached.
 */
@AutoConfigureMockMvc
class PatientDataExportIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User reception;
    private Long patientId;

    @BeforeEach
    void setUp() throws Exception {
        reception = createUser(Role.ADMINISTRATOR);
        patientId = TestPatients.insert(jdbc, "Daniela Sofía", "Zambrano Loor");
        jdbc.update("""
                INSERT INTO data_subject_requests (patient_id, request_type, received_on)
                VALUES (?, 'PORTABILITY', CURRENT_DATE)""", patientId);
        // A clinical record that must never appear in this export
        perform(createUser(Role.PHYSIOTHERAPIST), post("/api/patients/{id}/clinical-record", patientId)
                .contentType(MediaType.APPLICATION_JSON).content(TestClinicalRecords.record("Fuma a diario")))
                .andExpect(status().isCreated());
    }

    @Test
    void theJsonHasThePersonalDataTheConsentsAndTheRequestsButNoClinicalData() throws Exception {
        String body = perform(reception, get("/api/patients/{id}/data-export.json", patientId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        startsWith("attachment; filename=\"datos-paciente-" + patientId + "-")))
                .andExpect(jsonPath("$.patient.firstName").value("Daniela Sofía"))
                .andExpect(jsonPath("$.patient.nationalId").value(TestPatients.nationalIdOf(jdbc, patientId)))
                .andExpect(jsonPath("$.patient.address").isNotEmpty())
                .andExpect(jsonPath("$.portalAccount").doesNotExist())
                .andExpect(jsonPath("$.consents[0].type").value("DATA_PROCESSING"))
                .andExpect(jsonPath("$.requests[0].type").value("PORTABILITY"))
                .andExpect(jsonPath("$.generatedBy").value("Usuario de exportación"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Fuma a diario", "habits", "treatments", "diagnosis", "M54.5",
                "Dolor lumbar");
        assertThat(exports()).isEqualTo(1);
    }

    @Test
    void thePdfHasTheSameDataAndSaysThatTheClinicalRecordIsExportedByThePhysiotherapist() throws Exception {
        byte[] pdf = perform(reception, get("/api/patients/{id}/data-export.pdf", patientId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text).contains("Datos personales del paciente", "Daniela Sofía Zambrano Loor",
                    TestPatients.nationalIdOf(jdbc, patientId), "Tratamiento de datos personales y de salud",
                    "Portabilidad", "no incluye la historia clínica").doesNotContain("Fuma a diario", "M54.5");
        }
        assertThat(exports()).isEqualTo(1);
    }

    @Test
    void onlyTheReceptionExportsAndAnUnknownPatientIsNotFound() throws Exception {
        perform(createUser(Role.PHYSIOTHERAPIST), get("/api/patients/{id}/data-export.json", patientId))
                .andExpect(status().isForbidden());
        perform(createUser(Role.OPERATIONS_DIRECTOR), get("/api/patients/{id}/data-export.pdf", patientId))
                .andExpect(status().isForbidden());
        perform(reception, get("/api/patients/{id}/data-export.json", 999_999_999L))
                .andExpect(status().isNotFound());
        assertThat(exports()).isZero();
    }

    private int exports() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'EXPORT' AND entity_type = 'Patient' AND entity_id = ?
                AND user_id = ?""", Integer.class, patientId, reception.getId());
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(user, Instant.now()).token()));
    }

    private User createUser(Role role) {
        return userRepository.save(User.create("exportacion-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", "Usuario de exportación", role, null, Instant.now()));
    }

}
