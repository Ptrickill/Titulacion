package ec.rehabelite.sessions.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestSessions;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * HU-10: registration of therapy sessions with a server-assigned sequence number and responsible, validation, the
 * list of a treatment, and correction only by the physiotherapist who registered the session. No session is ever
 * deleted.
 */
@AutoConfigureMockMvc
class TherapySessionIntegrationTests extends AbstractIntegrationTest {

    private static final String ONLY_THE_AUTHOR =
            "Solo el fisioterapeuta que registró la sesión puede corregirla.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User physio;
    private Long treatmentId;

    @BeforeEach
    void setUp() {
        physio = createPhysio("Lcda. Valeria Andrade");
        treatmentId = TestTreatments.insertActive(jdbc);
    }

    // ----- registration -----

    @Test
    void registersTheSessionWithTheSequenceNumberAndTheAuthenticatedResponsible() throws Exception {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        String body = perform(physio, post("/api/treatments/{id}/therapy-sessions", treatmentId)
                        .contentType(MediaType.APPLICATION_JSON).content(TestSessions.body(4)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith(
                        "/api/therapy-sessions/")))
                .andExpect(jsonPath("$.treatmentId").value(treatmentId))
                .andExpect(jsonPath("$.sequenceNumber").value(1))
                .andExpect(jsonPath("$.sessionDate").value(yesterday.toString()))
                .andExpect(jsonPath("$.sessionTime").value("09:30:00"))
                .andExpect(jsonPath("$.painScore").value(4))
                .andExpect(jsonPath("$.progressStatus").value("IMPROVING"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.patientInstructions").value("Calor local 15 minutos en casa"))
                .andExpect(jsonPath("$.responsible.id").value(physio.getId()))
                .andExpect(jsonPath("$.responsible.fullName").value("Lcda. Valeria Andrade"))
                .andExpect(jsonPath("$.updatedAt").isEmpty())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM therapy_sessions WHERE id = ?", idOf(body));
        assertThat(row).containsEntry("treatment_id", treatmentId)
                .containsEntry("responsible_id", physio.getId())
                .containsEntry("created_by", physio.getId())
                .containsEntry("sequence_number", 1)
                .containsEntry("pain_score", 4);
    }

    @Test
    void sequenceNumbersAreConsecutiveWithinEachTreatment() throws Exception {
        User other = createPhysio("Lcdo. Mateo Ríos");
        Long otherTreatment = TestTreatments.insertActive(jdbc);

        assertThat(List.of(register(physio, treatmentId), register(other, treatmentId), register(physio, treatmentId)))
                .extracting(TherapySessionIntegrationTests::sequenceOf).containsExactly(1, 2, 3);
        assertThat(sequenceOf(register(physio, otherTreatment))).isEqualTo(1);
    }

    @Test
    void theResponsibleAndSequenceNumberSentByTheClientAreIgnored() throws Exception {
        User other = createPhysio("Lcdo. Mateo Ríos");
        String extra = """
                "responsibleId":%d,"responsible_id":%d,"sequenceNumber":99,"sequence_number":99,"""
                .formatted(other.getId(), other.getId());

        perform(physio, post("/api/treatments/{id}/therapy-sessions", treatmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(LocalDate.now().minusDays(1), 4, "Ejercicios", extra)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenceNumber").value(1))
                .andExpect(jsonPath("$.responsible.id").value(physio.getId()));
    }

    @Test
    void aPainScoreOutsideZeroToTenIsRejected() throws Exception {
        for (int painScore : new int[] {11, -1}) {
            perform(physio, post("/api/treatments/{id}/therapy-sessions", treatmentId)
                            .contentType(MediaType.APPLICATION_JSON).content(TestSessions.body(painScore)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field", hasItem("painScore")));
        }
        assertThat(sessionCount(treatmentId)).isZero();
    }

    @Test
    void anEmptyRequiredFieldIsRejected() throws Exception {
        perform(physio, post("/api/treatments/{id}/therapy-sessions", treatmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(LocalDate.now().minusDays(1), 4, "   ", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("activitiesPerformed")));
        perform(physio, post("/api/treatments/{id}/therapy-sessions", treatmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(4).replace(",\"status\":\"COMPLETED\"", "")
                                .replace("\"clinicalObservations\":\"Buena tolerancia al ejercicio\"",
                                        "\"clinicalObservations\":\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("status")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("clinicalObservations")));
        assertThat(sessionCount(treatmentId)).isZero();
    }

    @Test
    void theInstructionsForThePatientAreOptional() throws Exception {
        perform(physio, post("/api/treatments/{id}/therapy-sessions", treatmentId)
                        .contentType(MediaType.APPLICATION_JSON).content(TestSessions.body(4)
                                .replace("\"Calor local 15 minutos en casa\"", "\"  \"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientInstructions").isEmpty());
    }

    @Test
    void aFutureDateIsRejected() throws Exception {
        perform(physio, post("/api/treatments/{id}/therapy-sessions", treatmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(LocalDate.now().plusDays(2), 4, "Ejercicios", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", contains("sessionDate")));
    }

    @Test
    void aDateBeforeTheStartOfTheTreatmentIsRejected() throws Exception {
        perform(physio, post("/api/treatments/{id}/therapy-sessions", treatmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(LocalDate.now().minusDays(31), 4, "Ejercicios", "")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("La fecha de la sesión no puede ser anterior al inicio del tratamiento."));
    }

    @Test
    void sessionsAreOnlyRegisteredOnActiveTreatments() throws Exception {
        for (String status : new String[] {"COMPLETED", "ABANDONED"}) {
            Long closed = TestTreatments.insert(jdbc, status, LocalDate.now().minusDays(30));

            perform(physio, post("/api/treatments/{id}/therapy-sessions", closed)
                            .contentType(MediaType.APPLICATION_JSON).content(TestSessions.body(4)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value("Solo se pueden registrar sesiones en un tratamiento activo."));
            assertThat(sessionCount(closed)).isZero();
        }
    }

    @Test
    void anUnknownTreatmentAnswers404() throws Exception {
        perform(physio, post("/api/treatments/{id}/therapy-sessions", 999_999_999L)
                        .contentType(MediaType.APPLICATION_JSON).content(TestSessions.body(4)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El tratamiento no existe."));
        perform(physio, get("/api/treatments/{id}/therapy-sessions", 999_999_999L))
                .andExpect(status().isNotFound());
    }

    // ----- list and detail -----

    @Test
    void listsTheSessionsOfTheTreatmentBySequenceNumberWithTheResponsibleName() throws Exception {
        User other = createPhysio("Lcdo. Mateo Ríos");
        register(physio, treatmentId);
        register(other, treatmentId);
        register(physio, treatmentId);
        register(physio, TestTreatments.insertActive(jdbc));

        perform(physio, get("/api/treatments/{id}/therapy-sessions", treatmentId).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].sequenceNumber", contains(1, 2)))
                .andExpect(jsonPath("$.content[*].responsible.fullName",
                        contains("Lcda. Valeria Andrade", "Lcdo. Mateo Ríos")))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        perform(physio, get("/api/treatments/{id}/therapy-sessions", treatmentId).param("page", "1")
                        .param("size", "2"))
                .andExpect(jsonPath("$.content[*].sequenceNumber", contains(3)));
        perform(physio, get("/api/treatments/{id}/therapy-sessions", treatmentId).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anySessionCanBeReadByAnyPhysiotherapist() throws Exception {
        Long sessionId = idOf(register(physio, treatmentId));
        User other = createPhysio("Lcdo. Mateo Ríos");

        perform(other, get("/api/therapy-sessions/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sequenceNumber").value(1))
                .andExpect(jsonPath("$.responsible.fullName").value("Lcda. Valeria Andrade"));
        perform(other, get("/api/therapy-sessions/{id}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La sesión no existe."));
    }

    // ----- correction -----

    @Test
    void theAuthorCorrectsTheSessionKeepingTheSequenceNumberAndTheResponsible() throws Exception {
        register(physio, treatmentId);
        Long sessionId = idOf(register(physio, treatmentId));
        User other = createPhysio("Lcdo. Mateo Ríos");
        String extra = "\"responsibleId\":%d,\"sequenceNumber\":7,".formatted(other.getId());

        perform(physio, put("/api/therapy-sessions/{id}", sessionId).contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(LocalDate.now().minusDays(2), 3, "Ejercicios de Williams", extra)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sequenceNumber").value(2))
                .andExpect(jsonPath("$.responsible.id").value(physio.getId()))
                .andExpect(jsonPath("$.painScore").value(3))
                .andExpect(jsonPath("$.activitiesPerformed").value("Ejercicios de Williams"))
                .andExpect(jsonPath("$.updatedBy").value(physio.getId()))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        assertThat(jdbc.queryForMap("""
                SELECT sequence_number, responsible_id, pain_score, session_date, updated_by
                FROM therapy_sessions WHERE id = ?""", sessionId))
                .containsEntry("sequence_number", 2)
                .containsEntry("responsible_id", physio.getId())
                .containsEntry("pain_score", 3)
                .containsEntry("session_date", java.sql.Date.valueOf(LocalDate.now().minusDays(2)))
                .containsEntry("updated_by", physio.getId());
    }

    @Test
    void anotherPhysiotherapistCannotCorrectTheSessionAndTheAttemptIsAudited() throws Exception {
        Long sessionId = idOf(register(physio, treatmentId));
        User other = createPhysio("Lcdo. Mateo Ríos");

        perform(other, put("/api/therapy-sessions/{id}", sessionId).contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(LocalDate.now().minusDays(1), 9, "Otro texto", "")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.detail").value(ONLY_THE_AUTHOR))
                .andExpect(jsonPath("$.instance").value("/api/therapy-sessions/" + sessionId));

        assertThat(jdbc.queryForList("SELECT action, entity_type, entity_id FROM audit_logs WHERE user_id = ?",
                other.getId()))
                .containsExactly(Map.of("action", "ACCESS_DENIED", "entity_type", "therapy-sessions",
                        "entity_id", sessionId));
        assertThat(jdbc.queryForMap("SELECT pain_score, updated_at FROM therapy_sessions WHERE id = ?", sessionId))
                .containsEntry("pain_score", 4)
                .containsEntry("updated_at", null);
    }

    @Test
    void aSessionOfAFinishedTreatmentCanStillBeCorrectedByItsAuthor() throws Exception {
        Long sessionId = idOf(register(physio, treatmentId));
        jdbc.update("UPDATE treatments SET status = 'COMPLETED' WHERE id = ?", treatmentId);

        perform(physio, put("/api/therapy-sessions/{id}", sessionId).contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.painScore").value(2));
    }

    @Test
    void correctingWithTheSameDataChangesNothing() throws Exception {
        Long sessionId = idOf(register(physio, treatmentId));

        perform(physio, put("/api/therapy-sessions/{id}", sessionId).contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").isEmpty());

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'UPDATE' AND entity_type = 'TherapySession'
                AND entity_id = ?""", Integer.class, sessionId)).isZero();
    }

    @Test
    void aCorrectionIsValidatedLikeARegistration() throws Exception {
        Long sessionId = idOf(register(physio, treatmentId));

        perform(physio, put("/api/therapy-sessions/{id}", sessionId).contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(11)))
                .andExpect(status().isBadRequest());
        perform(physio, put("/api/therapy-sessions/{id}", sessionId).contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(LocalDate.now().minusDays(31), 4, "Ejercicios", "")))
                .andExpect(status().isUnprocessableContent());
        perform(physio, put("/api/therapy-sessions/{id}", 999_999_999L).contentType(MediaType.APPLICATION_JSON)
                        .content(TestSessions.body(4)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSessionCannotBeDeleted() throws Exception {
        Long sessionId = idOf(register(physio, treatmentId));

        String allowed = perform(physio, delete("/api/therapy-sessions/{id}", sessionId))
                .andExpect(status().isMethodNotAllowed())
                .andReturn().getResponse().getHeader(HttpHeaders.ALLOW);

        // The order of the methods is not fixed
        assertThat(allowed.split(",\\s*")).containsExactlyInAnyOrder("GET", "PUT");

        assertThat(sessionCount(treatmentId)).isEqualTo(1);
    }

    // ----- helpers -----

    private String register(User user, Long treatment) throws Exception {
        return perform(user, post("/api/treatments/{id}/therapy-sessions", treatment)
                        .contentType(MediaType.APPLICATION_JSON).content(TestSessions.body(4)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static Long idOf(String body) {
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static int sequenceOf(String body) {
        return JsonPath.read(body, "$.sequenceNumber");
    }

    private Integer sessionCount(Long treatment) {
        return jdbc.queryForObject("SELECT count(*) FROM therapy_sessions WHERE treatment_id = ?", Integer.class,
                treatment);
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(user, Instant.now()).token()));
    }

    private User createPhysio(String fullName) {
        return userRepository.save(User.create("sesiones-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, Role.PHYSIOTHERAPIST, null, Instant.now()));
    }

}
