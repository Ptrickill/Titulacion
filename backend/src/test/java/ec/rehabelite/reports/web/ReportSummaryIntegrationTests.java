package ec.rehabelite.reports.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-14: GET /api/reports/summary. The treatments are counted from their status (HU-08) and filtered by status and by
 * current responsible; the sessions are filtered by who registered them and by the status of their treatment. The
 * shared database is not empty, so the counts without a physiotherapist are compared before and after inserting.
 */
@AutoConfigureMockMvc
class ReportSummaryIntegrationTests extends AbstractIntegrationTest {

    private static final String SUMMARY = "/api/reports/summary";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User director;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        director = createUser(Role.OPERATIONS_DIRECTOR, "Dirección Operativa");
        today = LocalDate.now();
    }

    @Test
    void theTreatmentsOfAPhysiotherapistAreCountedPerStatusAndTheirSessionsPerStatus() throws Exception {
        Long valeria = createUser(Role.PHYSIOTHERAPIST, "Valeria Andrade").getId();
        Long active = treatmentOf(valeria, "ACTIVE", 30);
        treatmentOf(valeria, "ACTIVE", 20);
        Long completed = treatmentOf(valeria, "COMPLETED", 120);
        treatmentOf(valeria, "ABANDONED", 60);
        sessions(active, valeria, "COMPLETED", "COMPLETED", "COMPLETED", "RESCHEDULED");
        sessions(completed, valeria, "COMPLETED", "COMPLETED", "NO_SHOW");

        summary(Map.of("physiotherapistId", valeria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treatments.active").value(2))
                .andExpect(jsonPath("$.treatments.completed").value(1))
                .andExpect(jsonPath("$.treatments.abandoned").value(1))
                .andExpect(jsonPath("$.sessions.total").value(7))
                .andExpect(jsonPath("$.sessions.completed").value(5))
                .andExpect(jsonPath("$.sessions.rescheduled").value(1))
                .andExpect(jsonPath("$.sessions.noShow").value(1))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist.length()").value(1))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist[0].physiotherapistId").value(valeria))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist[0].name").value("Valeria Andrade"))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist[0].sessions").value(7))
                .andExpect(jsonPath("$.averageSessionsPerPhysiotherapist").value(7.0));
    }

    @Test
    void theStatusAndThePhysiotherapistCanBeCombined() throws Exception {
        Long valeria = createUser(Role.PHYSIOTHERAPIST, "Valeria Andrade").getId();
        Long active = treatmentOf(valeria, "ACTIVE", 30);
        Long completed = treatmentOf(valeria, "COMPLETED", 120);
        treatmentOf(valeria, "COMPLETED", 200);
        sessions(active, valeria, "COMPLETED", "COMPLETED");
        sessions(completed, valeria, "COMPLETED", "NO_SHOW", "RESCHEDULED");

        summary(Map.of("status", "COMPLETED", "physiotherapistId", valeria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treatments.active").value(0))
                .andExpect(jsonPath("$.treatments.completed").value(2))
                .andExpect(jsonPath("$.treatments.abandoned").value(0))
                // Only the sessions of the finished treatments
                .andExpect(jsonPath("$.sessions.total").value(3))
                .andExpect(jsonPath("$.sessions.completed").value(1))
                .andExpect(jsonPath("$.sessions.rescheduled").value(1))
                .andExpect(jsonPath("$.sessions.noShow").value(1));
    }

    @Test
    void theStatusAloneFiltersTheTreatmentsAndTheSessionsOfTheTreatmentsInThatStatus() throws Exception {
        Map<String, Object> before = body(summary(Map.of("status", "ABANDONED")));
        Long mateo = createUser(Role.PHYSIOTHERAPIST, "Mateo Ríos").getId();
        sessions(treatmentOf(mateo, "ABANDONED", 90), mateo, "COMPLETED", "NO_SHOW");
        // An active treatment and its sessions do not count with this filter
        sessions(treatmentOf(mateo, "ACTIVE", 10), mateo, "COMPLETED");

        Map<String, Object> after = body(summary(Map.of("status", "ABANDONED")));

        assertThat(number(after, "$.treatments.active")).isZero();
        assertThat(number(after, "$.treatments.completed")).isZero();
        assertThat(number(after, "$.treatments.abandoned") - number(before, "$.treatments.abandoned")).isEqualTo(1);
        assertThat(number(after, "$.sessions.total") - number(before, "$.sessions.total")).isEqualTo(2);
        assertThat(number(after, "$.sessions.noShow") - number(before, "$.sessions.noShow")).isEqualTo(1);
        List<Map<String, Object>> physiotherapists = JsonPath.read(after, "$.sessionsByPhysiotherapist[?(@.physiotherapistId == "
                + mateo + ")]");
        assertThat(physiotherapists).singleElement().satisfies(row -> assertThat(row).containsEntry("sessions", 2));
    }

    @Test
    void withoutFilterEveryTreatmentAndEverySessionCountsAndTheAverageIsTheTotalOverThePhysiotherapists()
            throws Exception {
        Map<String, Object> before = body(summary(Map.of()));
        Long valeria = createUser(Role.PHYSIOTHERAPIST, "Valeria Andrade").getId();
        Long mateo = createUser(Role.PHYSIOTHERAPIST, "Mateo Ríos").getId();
        Long active = treatmentOf(valeria, "ACTIVE", 30);
        sessions(active, valeria, "COMPLETED", "COMPLETED", "RESCHEDULED");
        sessions(treatmentOf(mateo, "COMPLETED", 100), mateo, "COMPLETED");
        // A treatment without responsible also counts
        TestTreatments.insert(jdbc, "ABANDONED", today.minusDays(50));

        Map<String, Object> after = body(summary(Map.of()));

        assertThat(number(after, "$.treatments.active") - number(before, "$.treatments.active")).isEqualTo(1);
        assertThat(number(after, "$.treatments.completed") - number(before, "$.treatments.completed")).isEqualTo(1);
        assertThat(number(after, "$.treatments.abandoned") - number(before, "$.treatments.abandoned")).isEqualTo(1);
        assertThat(number(after, "$.sessions.total") - number(before, "$.sessions.total")).isEqualTo(4);
        assertThat(number(after, "$.sessions.completed") - number(before, "$.sessions.completed")).isEqualTo(3);
        assertThat(number(after, "$.sessions.rescheduled") - number(before, "$.sessions.rescheduled")).isEqualTo(1);
        // The total of the physiotherapists is the total of sessions, and the average divides it among them
        List<Integer> perPhysiotherapist = JsonPath.read(after, "$.sessionsByPhysiotherapist[*].sessions");
        long total = number(after, "$.sessions.total");
        assertThat(perPhysiotherapist.stream().mapToLong(Integer::longValue).sum()).isEqualTo(total);
        assertThat(new BigDecimal(JsonPath.read(after, "$.averageSessionsPerPhysiotherapist").toString()))
                .isEqualByComparingTo(BigDecimal.valueOf(total)
                        .divide(BigDecimal.valueOf(perPhysiotherapist.size()), 1, RoundingMode.HALF_UP));
        // From the most sessions to the fewest
        assertThat(perPhysiotherapist).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
    }

    /**
     * A replacement: Valeria registers sessions in a treatment whose current responsible becomes Mateo. Her sessions
     * keep counting for her, but the treatment is now Mateo's; his sessions in it are only those he registered.
     */
    @Test
    void afterAReplacementTheSessionsCountForWhoRegisteredThemAndTheTreatmentForTheCurrentResponsible()
            throws Exception {
        Long valeria = createUser(Role.PHYSIOTHERAPIST, "Valeria Andrade").getId();
        Long mateo = createUser(Role.PHYSIOTHERAPIST, "Mateo Ríos").getId();
        Long handedOver = TestTreatments.insert(jdbc, "ACTIVE", today.minusDays(40));
        jdbc.update("""
                INSERT INTO responsible_assignments (treatment_id, physiotherapist_id, valid_from, valid_to)
                VALUES (?, ?, now() - INTERVAL '40 days', now() - INTERVAL '1 day')""", handedOver, valeria);
        TestTreatments.assignResponsible(jdbc, handedOver, mateo);
        sessions(handedOver, valeria, "COMPLETED", "COMPLETED", "NO_SHOW");
        TestSessions.insert(jdbc, handedOver, mateo, 4, today.minusDays(1), "10:00", 4, "COMPLETED");

        // Valeria: her three sessions count; the treatment is no longer hers, so it is not counted nor listed
        summary(Map.of("physiotherapistId", valeria))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treatments.active").value(0))
                .andExpect(jsonPath("$.sessions.total").value(3))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist.length()").value(1))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist[0].physiotherapistId").value(valeria))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist[0].sessions").value(3));
        perform(get("/api/reports/treatments").param("physiotherapistId", valeria.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Mateo: the treatment is his and is listed; only the session he registered counts for him
        summary(Map.of("physiotherapistId", mateo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treatments.active").value(1))
                .andExpect(jsonPath("$.sessions.total").value(1))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist.length()").value(1))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist[0].physiotherapistId").value(mateo))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist[0].sessions").value(1));
        perform(get("/api/reports/treatments").param("physiotherapistId", mateo.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(handedOver))
                .andExpect(jsonPath("$.content[0].responsibleName").value("Mateo Ríos"));
    }

    @Test
    void aPhysiotherapistWithoutTreatmentsNorSessionsHasEverythingInZero() throws Exception {
        Long newcomer = createUser(Role.PHYSIOTHERAPIST, "Julián Rivas").getId();

        summary(Map.of("physiotherapistId", newcomer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treatments.active").value(0))
                .andExpect(jsonPath("$.treatments.completed").value(0))
                .andExpect(jsonPath("$.treatments.abandoned").value(0))
                .andExpect(jsonPath("$.sessions.total").value(0))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist").isEmpty())
                .andExpect(jsonPath("$.averageSessionsPerPhysiotherapist").value(0.0));
    }

    @Test
    void anInvalidFilterIsRejectedWith400AndIsNotAudited() throws Exception {
        summary(Map.of("status", "PAUSED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
        summary(Map.of("physiotherapistId", "abc")).andExpect(status().isBadRequest());

        assertThat(auditEntries()).isEmpty();
    }

    @Test
    void eachSummaryIsAuditedAsOneReadOfTheReport() throws Exception {
        summary(Map.of()).andExpect(status().isOk());
        summary(Map.of("status", "ACTIVE")).andExpect(status().isOk());

        assertThat(auditEntries()).hasSize(2).allSatisfy(entry -> assertThat(entry)
                .containsEntry("action", "READ")
                .containsEntry("entity_type", "Report")
                .containsEntry("entity_id", null)
                .containsEntry("ip_address", "127.0.0.1"));
    }

    @Test
    void theReportIsReadOnly() throws Exception {
        perform(post(SUMMARY))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET"));
    }

    // ----- helpers -----

    /** A treatment of a new patient that started the given days ago, with the physiotherapist as responsible. */
    private Long treatmentOf(Long physiotherapistId, String status, int daysAgo) {
        Long treatmentId = TestTreatments.insert(jdbc, status, today.minusDays(daysAgo));
        TestTreatments.assignResponsible(jdbc, treatmentId, physiotherapistId);
        return treatmentId;
    }

    /** One session per status, registered by the physiotherapist on consecutive past days. */
    private void sessions(Long treatmentId, Long physiotherapistId, String... statuses) {
        for (int i = 0; i < statuses.length; i++) {
            TestSessions.insert(jdbc, treatmentId, physiotherapistId, i + 1, today.minusDays(10L - i), "09:00", 5,
                    statuses[i]);
        }
    }

    private ResultActions summary(Map<String, Object> parameters) throws Exception {
        MockHttpServletRequestBuilder request = get(SUMMARY);
        parameters.forEach((name, value) -> request.param(name, value.toString()));
        return perform(request);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(director, Instant.now()).token()));
    }

    private static Map<String, Object> body(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$");
    }

    private static long number(Map<String, Object> body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private List<Map<String, Object>> auditEntries() {
        return jdbc.queryForList("SELECT action, entity_type, entity_id, ip_address FROM audit_logs WHERE user_id = ?",
                director.getId());
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("reportes-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

}
