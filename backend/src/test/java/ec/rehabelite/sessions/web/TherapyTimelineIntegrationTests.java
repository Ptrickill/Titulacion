package ec.rehabelite.sessions.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestSessions;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * HU-11: the therapy timeline of a treatment. Sessions in chronological order (date, time and sequence number) with
 * the names of their responsibles, inclusive date filters, the starting point of the treatment, and the pain curve,
 * which starts with the initial evaluation and leaves out RESCHEDULED and NO_SHOW sessions.
 * <p>
 * The treatment started 30 days ago (EVA 7, 12 planned sessions, M54.5). Its sessions were registered out of
 * chronological order:
 * <pre>
 *  #3  day 2  08:00  COMPLETED    Mateo    EVA 5
 *  #1  day 2  10:00  COMPLETED    Valeria  EVA 6
 *  #2  day 5  08:00  RESCHEDULED  Mateo    EVA 6
 *  #4  day 5  08:00  NO_SHOW      Valeria  EVA 5   (same date and time as #2: the sequence number decides)
 *  #5  day 9  09:00  COMPLETED    Valeria  EVA 3
 * </pre>
 */
@AutoConfigureMockMvc
class TherapyTimelineIntegrationTests extends AbstractIntegrationTest {

    private static final String INVALID_RANGE = "La fecha inicial no puede ser posterior a la fecha final.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User valeria;
    private User mateo;
    private Long treatmentId;
    private LocalDate start;

    @BeforeEach
    void setUp() {
        valeria = createUser(Role.PHYSIOTHERAPIST, "Lcda. Valeria Andrade");
        mateo = createUser(Role.PHYSIOTHERAPIST, "Lcdo. Mateo Ríos");
        treatmentId = TestTreatments.insertActive(jdbc);
        start = jdbc.queryForObject("SELECT start_date FROM treatments WHERE id = ?", LocalDate.class, treatmentId);
        insert(1, valeria, 2, "10:00", 6, "COMPLETED");
        insert(2, mateo, 5, "08:00", 6, "RESCHEDULED");
        insert(3, mateo, 2, "08:00", 5, "COMPLETED");
        insert(4, valeria, 5, "08:00", 5, "NO_SHOW");
        insert(5, valeria, 9, "09:00", 3, "COMPLETED");
    }

    @Test
    void theSessionsAreInChronologicalOrderWithTheNamesOfTheirResponsibles() throws Exception {
        timeline(valeria, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[*].sequenceNumber", contains(3, 1, 2, 4, 5)))
                .andExpect(jsonPath("$.sessions[*].sessionDate", contains(day(2), day(2), day(5), day(5), day(9))))
                .andExpect(jsonPath("$.sessions[*].sessionTime",
                        contains("08:00:00", "10:00:00", "08:00:00", "08:00:00", "09:00:00")))
                .andExpect(jsonPath("$.sessions[*].responsible.fullName", contains("Lcdo. Mateo Ríos",
                        "Lcda. Valeria Andrade", "Lcdo. Mateo Ríos", "Lcda. Valeria Andrade",
                        "Lcda. Valeria Andrade")))
                .andExpect(jsonPath("$.sessions[*].status",
                        contains("COMPLETED", "COMPLETED", "RESCHEDULED", "NO_SHOW", "COMPLETED")))
                .andExpect(jsonPath("$.sessions[0].sessionType").value("Fisioterapia lumbar mecánica"))
                .andExpect(jsonPath("$.sessions[0].painScore").value(5))
                .andExpect(jsonPath("$.sessions[0].progressStatus").value("IMPROVING"))
                .andExpect(jsonPath("$.sessions[0].clinicalObservations").value("Buena tolerancia al ejercicio"))
                .andExpect(jsonPath("$.sessions[0].patientInstructions").value("Calor local 15 minutos en casa"))
                .andExpect(jsonPath("$.sessions[0].responsible.id").value(mateo.getId()))
                .andExpect(jsonPath("$.from").isEmpty())
                .andExpect(jsonPath("$.to").isEmpty());
    }

    @Test
    void theTimelineHasTheStartingPointOfTheTreatment() throws Exception {
        Long patientId = jdbc.queryForObject("""
                SELECT r.patient_id FROM treatments t JOIN clinical_records r ON r.id = t.clinical_record_id
                WHERE t.id = ?""", Long.class, treatmentId);

        timeline(mateo, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treatment.id").value(treatmentId))
                .andExpect(jsonPath("$.treatment.patientId").value(patientId))
                .andExpect(jsonPath("$.treatment.active").value(true))
                .andExpect(jsonPath("$.treatment.startDate").value(start.toString()))
                .andExpect(jsonPath("$.treatment.initialPainScore").value(7))
                .andExpect(jsonPath("$.treatment.plannedSessions").value(12))
                .andExpect(jsonPath("$.treatment.completedSessions").value(3))
                .andExpect(jsonPath("$.treatment.diagnosis.code").value("M54.5"))
                .andExpect(jsonPath("$.treatment.diagnosis.description").isNotEmpty());
    }

    @Test
    void thePainCurveStartsWithTheInitialEvaluationAndLeavesOutRescheduledAndNoShowSessions() throws Exception {
        timeline(valeria, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.painCurve[*].label",
                        contains("Evaluación inicial", "Sesión 3", "Sesión 1", "Sesión 5")))
                .andExpect(jsonPath("$.painCurve[*].painScore", contains(7, 5, 6, 3)))
                .andExpect(jsonPath("$.painCurve[*].date", contains(start.toString(), day(2), day(2), day(9))))
                .andExpect(jsonPath("$.painCurve[0].sessionId").isEmpty())
                .andExpect(jsonPath("$.painCurve[0].sequenceNumber").isEmpty())
                .andExpect(jsonPath("$.painCurve[1].sequenceNumber").value(3));
    }

    @Test
    void theDateFiltersAreInclusive() throws Exception {
        timeline(valeria, day(5), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(day(5)))
                .andExpect(jsonPath("$.sessions[*].sequenceNumber", contains(2, 4, 5)));
        timeline(valeria, null, day(5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value(day(5)))
                .andExpect(jsonPath("$.sessions[*].sequenceNumber", contains(3, 1, 2, 4)));
        timeline(valeria, day(2), day(2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[*].sequenceNumber", contains(3, 1)));
    }

    @Test
    void theCurveFollowsTheFilterButAlwaysStartsWithTheInitialEvaluation() throws Exception {
        timeline(valeria, day(5), day(9))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.painCurve[*].label", contains("Evaluación inicial", "Sesión 5")))
                // The progress of the plan counts every completed session, whatever the filter
                .andExpect(jsonPath("$.treatment.completedSessions").value(3));
        timeline(valeria, day(5), day(5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[*].sequenceNumber", contains(2, 4)))
                .andExpect(jsonPath("$.painCurve[*].label", contains("Evaluación inicial")));
    }

    @Test
    void aRangeWithoutSessionsIsEmpty() throws Exception {
        timeline(valeria, day(20), day(25))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions", empty()))
                .andExpect(jsonPath("$.painCurve.length()").value(1));
    }

    @Test
    void aStartAfterTheEndIsRejectedAndNotAudited() throws Exception {
        timeline(valeria, day(9), day(5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud no válida"))
                .andExpect(jsonPath("$.detail").value(INVALID_RANGE));

        assertThat(auditEntriesOf(valeria)).isEmpty();
    }

    @Test
    void anUnknownTreatmentIsNotFoundAndNotAudited() throws Exception {
        perform(valeria, get("/api/treatments/{id}/timeline", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El tratamiento no existe."));

        assertThat(auditEntriesOf(valeria)).isEmpty();
    }

    @Test
    void eachReadIsAuditedAsReadOfTheTreatment() throws Exception {
        User reader = createUser(Role.PHYSIOTHERAPIST, "Lcda. Paula Mena");

        timeline(reader, null, null).andExpect(status().isOk());
        timeline(reader, day(5), null).andExpect(status().isOk());

        assertThat(auditEntriesOf(reader)).hasSize(2).allSatisfy(entry -> assertThat(entry)
                .containsEntry("action", "READ")
                .containsEntry("entity_type", "Treatment")
                .containsEntry("entity_id", treatmentId));
    }

    @Test
    void otherRolesAreDeniedAndTheAttemptIsAudited() throws Exception {
        for (Role role : List.of(Role.ADMINISTRATOR, Role.OPERATIONS_DIRECTOR, Role.PATIENT)) {
            User user = createUser(role, "Usuario de prueba");

            timeline(user, null, null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Acceso denegado"));

            assertThat(auditEntriesOf(user)).singleElement().satisfies(entry -> assertThat(entry)
                    .containsEntry("action", "ACCESS_DENIED")
                    .containsEntry("entity_type", "treatments")
                    .containsEntry("entity_id", treatmentId));
        }
    }

    // ----- helpers -----

    private void insert(int sequenceNumber, User responsible, int day, String time, int painScore, String status) {
        TestSessions.insert(jdbc, treatmentId, sequenceNumber, responsible.getId(), start.plusDays(day),
                LocalTime.parse(time), painScore, status);
    }

    /** Day {@code n} of the treatment as the API writes it. */
    private String day(int n) {
        return start.plusDays(n).toString();
    }

    private ResultActions timeline(User user, String from, String to) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/treatments/{id}/timeline", treatmentId);
        if (from != null) {
            request.param("from", from);
        }
        if (to != null) {
            request.param("to", to);
        }
        return perform(user, request.accept(MediaType.APPLICATION_JSON));
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(user, Instant.now()).token()));
    }

    private List<Map<String, Object>> auditEntriesOf(User user) {
        return jdbc.queryForList("SELECT action, entity_type, entity_id FROM audit_logs WHERE user_id = ?",
                user.getId());
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("linea-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

}
