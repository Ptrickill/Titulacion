package ec.rehabelite.reports.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestClinicalText;
import ec.rehabelite.TestSessions;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-15: the date range of the reports, inclusive and in days of Quito. It filters the treatments by their period (in
 * progress some day of the range) for the counts per status and the list, and the sessions by their date for the
 * session indicators, the bars and the average. Each test uses its own physiotherapist, so the counts are exact in the
 * shared database. The range is October 2025, always in the past.
 */
@AutoConfigureMockMvc
class ReportDateRangeIntegrationTests extends AbstractIntegrationTest {

    private static final LocalDate FROM = LocalDate.parse("2025-10-01");
    private static final LocalDate TO = LocalDate.parse("2025-10-31");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    private User director;

    @BeforeEach
    void setUp() {
        director = createUser(Role.OPERATIONS_DIRECTOR, "Dirección Operativa");
    }

    @Test
    void theSessionsOfTheFirstAndOfTheLastDayCountAndThoseOfTheDaysAroundDoNot() throws Exception {
        Long valeria = createUser(Role.PHYSIOTHERAPIST, "Valeria Andrade").getId();
        Long treatment = treatmentOf(valeria, "ACTIVE", "2025-09-01");
        TestSessions.insert(jdbc, treatment, valeria, 1, FROM.minusDays(1), "23:30", 5, "COMPLETED");
        TestSessions.insert(jdbc, treatment, valeria, 2, FROM, "07:00", 5, "COMPLETED");
        TestSessions.insert(jdbc, treatment, valeria, 3, FROM.plusDays(10), "09:00", 5, "RESCHEDULED");
        TestSessions.insert(jdbc, treatment, valeria, 4, TO, "19:30", 5, "NO_SHOW");
        TestSessions.insert(jdbc, treatment, valeria, 5, TO.plusDays(1), "07:00", 5, "COMPLETED");

        summary(valeria, FROM, TO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.total").value(3))
                .andExpect(jsonPath("$.sessions.completed").value(1))
                .andExpect(jsonPath("$.sessions.rescheduled").value(1))
                .andExpect(jsonPath("$.sessions.noShow").value(1))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist.length()").value(1))
                .andExpect(jsonPath("$.sessionsByPhysiotherapist[0].sessions").value(3))
                .andExpect(jsonPath("$.averageSessionsPerPhysiotherapist").value(3.0));
        // A range of one day; and either bound alone
        summary(valeria, TO, TO).andExpect(jsonPath("$.sessions.total").value(1));
        summary(valeria, FROM, null).andExpect(jsonPath("$.sessions.total").value(4));
        summary(valeria, null, FROM).andExpect(jsonPath("$.sessions.total").value(2));
        summary(valeria, null, null).andExpect(jsonPath("$.sessions.total").value(5));
    }

    @Test
    void theTreatmentsAreCountedAndListedWhenTheyWereInProgressSomeDayOfTheRange() throws Exception {
        Long mateo = createUser(Role.PHYSIOTHERAPIST, "Mateo Ríos").getId();
        Long startedBefore = treatmentOf(mateo, "ACTIVE", "2025-09-01");
        Long closedBefore = treatmentOf(mateo, "COMPLETED", "2025-08-01");
        closedAt(closedBefore, mateo, "COMPLETED", "2025-09-30T15:00:00Z");
        // 23:30 of September 30 in Quito (already October 1 in UTC): closed before the range
        Long closedTheNightBefore = treatmentOf(mateo, "ABANDONED", "2025-08-01");
        closedAt(closedTheNightBefore, mateo, "ABANDONED", "2025-10-01T04:30:00Z");
        Long closedInside = treatmentOf(mateo, "ABANDONED", "2025-08-15");
        closedAt(closedInside, mateo, "ABANDONED", "2025-10-20T15:00:00Z");
        Long startedTheLastDay = treatmentOf(mateo, "ACTIVE", "2025-10-31");
        treatmentOf(mateo, "ACTIVE", "2025-11-01");

        summary(mateo, FROM, TO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treatments.active").value(2))
                .andExpect(jsonPath("$.treatments.completed").value(0))
                .andExpect(jsonPath("$.treatments.abandoned").value(1));
        treatments(mateo, FROM, TO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(startedTheLastDay))
                .andExpect(jsonPath("$.content[1].id").value(startedBefore))
                .andExpect(jsonPath("$.content[2].id").value(closedInside))
                // The status is the current one
                .andExpect(jsonPath("$.content[2].status").value("ABANDONED"));
    }

    @Test
    void theStatusThePhysiotherapistAndTheRangeCanBeCombined() throws Exception {
        Long valeria = createUser(Role.PHYSIOTHERAPIST, "Valeria Andrade").getId();
        Long finished = treatmentOf(valeria, "COMPLETED", "2025-09-01");
        closedAt(finished, valeria, "COMPLETED", "2025-10-25T15:00:00Z");
        Long active = treatmentOf(valeria, "ACTIVE", "2025-09-15");
        TestSessions.insert(jdbc, finished, valeria, 1, LocalDate.parse("2025-09-20"), "09:00", 6, "COMPLETED");
        TestSessions.insert(jdbc, finished, valeria, 2, LocalDate.parse("2025-10-05"), "09:00", 5, "COMPLETED");
        TestSessions.insert(jdbc, finished, valeria, 3, LocalDate.parse("2025-10-12"), "09:00", 4, "NO_SHOW");
        // An active treatment: its session is in the range but not in the status
        TestSessions.insert(jdbc, active, valeria, 1, LocalDate.parse("2025-10-06"), "09:00", 6, "COMPLETED");

        summary(valeria, FROM, TO, "COMPLETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treatments.active").value(0))
                .andExpect(jsonPath("$.treatments.completed").value(1))
                .andExpect(jsonPath("$.sessions.total").value(2))
                .andExpect(jsonPath("$.sessions.completed").value(1))
                .andExpect(jsonPath("$.sessions.noShow").value(1));
        perform(treatmentsRequest(valeria, FROM, TO).param("status", "COMPLETED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(finished));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/reports/summary", "/api/reports/treatments", "/api/reports/summary.pdf"})
    void aRangeWhoseStartIsAfterItsEndIsRejectedWith400AndIsNotAudited(String path) throws Exception {
        perform(get(path).param("from", "2025-10-31").param("to", "2025-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha inicial no puede ser posterior a la fecha final."));
        perform(get(path).param("from", "31/10/2025")).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE user_id = ?", Long.class,
                director.getId())).isZero();
    }

    // ----- helpers -----

    /** A treatment of a new patient with the physiotherapist as current responsible. */
    private Long treatmentOf(Long physiotherapistId, String status, String startDate) {
        Long treatmentId = TestTreatments.insert(jdbc, status, LocalDate.parse(startDate));
        TestTreatments.assignResponsible(jdbc, treatmentId, physiotherapistId);
        return treatmentId;
    }

    /** The status change that closed the treatment, at the given instant. */
    private void closedAt(Long treatmentId, Long userId, String newStatus, String instant) {
        jdbc.update("""
                INSERT INTO treatment_status_changes (treatment_id, user_id, previous_status, new_status, reason,
                                                      changed_at)
                VALUES (?, ?, 'ACTIVE', ?, ?, ?)""", treatmentId, userId, newStatus,
                TestClinicalText.encrypt("Cierre de prueba"), Timestamp.from(Instant.parse(instant)));
    }

    private ResultActions summary(Long physiotherapistId, LocalDate from, LocalDate to, String... status)
            throws Exception {
        MockHttpServletRequestBuilder request = withRange(get("/api/reports/summary")
                .param("physiotherapistId", physiotherapistId.toString()), from, to);
        if (status.length > 0) {
            request.param("status", status[0]);
        }
        return perform(request);
    }

    private ResultActions treatments(Long physiotherapistId, LocalDate from, LocalDate to) throws Exception {
        return perform(treatmentsRequest(physiotherapistId, from, to));
    }

    private static MockHttpServletRequestBuilder treatmentsRequest(Long physiotherapistId, LocalDate from,
            LocalDate to) {
        return withRange(get("/api/reports/treatments").param("physiotherapistId", physiotherapistId.toString()),
                from, to);
    }

    private static MockHttpServletRequestBuilder withRange(MockHttpServletRequestBuilder request, LocalDate from,
            LocalDate to) {
        if (from != null) {
            request.param("from", from.toString());
        }
        if (to != null) {
            request.param("to", to.toString());
        }
        return request;
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenService.issue(director, Instant.now()).token()));
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("reportes-rango-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

}
