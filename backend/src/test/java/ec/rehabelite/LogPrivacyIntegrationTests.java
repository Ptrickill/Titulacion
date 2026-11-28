package ec.rehabelite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MEJ-06 (B5, D25): the main flows never write a name, a national ID, an email, a phone or clinical text to the logs:
 * registration and edition of a patient, consents, portal access, login and password reset, clinical record, treatment
 * status, sessions, requests of the data subject, erasure and both exports. The output of the whole run is captured
 * and searched for each synthetic value.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LogPrivacyIntegrationTests extends AbstractIntegrationTest {

    private static final String FIRST_NAME = "Zoraida Ximena";
    private static final String LAST_NAME = "Quintanilla Ubidia";
    private static final String PHONE = "0987123456";
    private static final String NEW_PHONE = "0987654123";
    private static final String HABITS = "Hábito privado Zeta Omega";
    private static final String OBSERVATION = "Observación clínica privada Kappa";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private RecordingMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theMainFlowsLogOnlyIdsAndCodes(CapturedOutput output) throws Exception {
        User reception = createUser(Role.ADMINISTRATOR);
        User physio = createUser(Role.PHYSIOTHERAPIST);
        User director = createUser(Role.OPERATIONS_DIRECTOR);
        String nationalId = TestPatients.randomNationalId();
        String email = "zoraida.privacidad-" + UUID.randomUUID() + "@correo.ec";
        String newEmail = "zoraida.nueva-" + UUID.randomUUID() + "@correo.ec";

        // Registration with consents, edition and portal access
        String patient = perform(reception, post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                .content(patientBody(nationalId, email, PHONE, true)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long patientId = ((Number) JsonPath.read(patient, "$.id")).longValue();
        perform(reception, put("/api/patients/{id}", patientId).contentType(MediaType.APPLICATION_JSON)
                .content(patientBody(null, email, NEW_PHONE, false))).andExpect(status().isOk());
        String account = perform(reception, post("/api/patients/{id}/portal-account", patientId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long userId = ((Number) JsonPath.read(account, "$.userId")).longValue();
        String activation = RecordingMailSender.tokenOf(mailSender.messagesTo(email).getFirst());
        mockMvc.perform(post("/api/auth/activate").contentType(MediaType.APPLICATION_JSON).content("""
                {"token":"%s","password":"Clave.Privada2026","passwordConfirmation":"Clave.Privada2026"}"""
                .formatted(activation))).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"Clave.Privada2026\"}".formatted(email)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"incorrecta1\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email))).andExpect(status().isAccepted());
        // Change of email of the activated account: confirmation link and notice to the previous address
        perform(reception, put("/api/patients/{id}", patientId).contentType(MediaType.APPLICATION_JSON)
                .content(patientBody(null, newEmail, NEW_PHONE, false))).andExpect(status().isOk());

        // Clinical record, session, correction and status change
        perform(physio, post("/api/patients/{id}/clinical-record", patientId).contentType(MediaType.APPLICATION_JSON)
                .content(TestClinicalRecords.record(HABITS))).andExpect(status().isCreated());
        Long treatmentId = jdbc.queryForObject("""
                SELECT t.id FROM treatments t JOIN clinical_records r ON r.id = t.clinical_record_id
                WHERE r.patient_id = ?""", Long.class, patientId);
        // The treatment started a month ago, so a session of yesterday is inside it
        jdbc.update("UPDATE treatments SET start_date = start_date - 30 WHERE id = ?", treatmentId);
        String session = perform(physio, post("/api/treatments/{id}/therapy-sessions", treatmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestSessions.body(LocalDate.now().minusDays(1), 5, OBSERVATION, "")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long sessionId = ((Number) JsonPath.read(session, "$.id")).longValue();
        perform(physio, put("/api/therapy-sessions/{id}", sessionId).contentType(MediaType.APPLICATION_JSON)
                .content(TestSessions.body(LocalDate.now().minusDays(1), 4, OBSERVATION + " corregida", "")))
                .andExpect(status().isOk());
        perform(physio, get("/api/patients/{id}/clinical-record/export.pdf", patientId)).andExpect(status().isOk());
        perform(physio, patch("/api/treatments/{id}/status", treatmentId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newStatus\":\"ABANDONED\",\"reason\":\"%s\"}".formatted(OBSERVATION)))
                .andExpect(status().isOk());

        // Consents, requests, export and erasure
        perform(reception, post("/api/patients/{id}/consents/EMAIL_COMMUNICATIONS/revocation", patientId))
                .andExpect(status().isOk());
        perform(reception, get("/api/patients/{id}/data-export.json", patientId)).andExpect(status().isOk());
        String request = perform(reception, post("/api/patients/{id}/data-requests", patientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ERASURE\",\"receivedOn\":\"%s\"}".formatted(LocalDate.now().minusDays(1))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long requestId = ((Number) JsonPath.read(request, "$.id")).longValue();
        perform(director, post("/api/data-requests/erasures/{id}/acceptance", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nationalId\":\"%s\",\"response\":\"Aceptada\"}".formatted(nationalId)))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(userId).orElseThrow().isActive()).isFalse();
        String logs = output.getAll();
        assertThat(logs).as("the flows did log something").contains("patientId=" + patientId);
        for (String value : List.of(FIRST_NAME, LAST_NAME, "Zoraida", "Quintanilla", nationalId, email, newEmail,
                "zoraida.", PHONE, NEW_PHONE, HABITS, OBSERVATION, "Dolor lumbar al levantar peso",
                "Clave.Privada2026")) {
            assertThat(logs).as("logs must not contain %s", value).doesNotContain(value);
        }
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(user, Instant.now()).token()));
    }

    private User createUser(Role role) {
        return userRepository.save(User.create("logs-" + UUID.randomUUID() + "@rehabelite.test", "not-a-real-hash",
                "Usuario de logs", role, null, Instant.now()));
    }

    /** POST (with the national ID and the consents) or PUT body of the patient. */
    private static String patientBody(String nationalId, String email, String phone, boolean registration) {
        String identification = nationalId == null ? "" : "\"nationalId\":\"" + nationalId + "\",";
        String consents = registration ? ",\"dataProcessingConsentVersion\":\"" + TestPatients.DATA_PROCESSING_VERSION
                + "\",\"emailCommunicationsConsentVersion\":\"" + TestPatients.EMAIL_COMMUNICATIONS_VERSION + "\""
                : "";
        return """
                {%s"admissionDate":"2026-01-15","firstName":"%s","lastName":"%s","birthDate":"1985-04-12",
                 "sex":"FEMALE","phone":"%s","email":"%s",
                 "address":{"provinceCode":"17","cantonCode":"1701","parishCode":"170156","mainStreet":"Av. Ilaló",
                            "crossStreet":"Río Pita","reference":"Frente al parque"},
                 "occupation":"Contadora","educationLevel":"Superior","hasHealthInsurance":true%s}"""
                .formatted(identification, FIRST_NAME, LAST_NAME, phone, email, consents);
    }

}
