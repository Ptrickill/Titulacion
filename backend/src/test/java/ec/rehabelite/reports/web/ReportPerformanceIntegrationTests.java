package ec.rehabelite.reports.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.JwtTokenService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-14, HU-15: the reports answer with a fixed number of SQL statements, without N+1. The summary, the list and the
 * PDF cost the same for a physiotherapist with one treatment and one session as for one with sixty treatments and three
 * hundred sessions, with or without a date range, and the summary without filter costs the same over the whole shared
 * database. Data is inserted with SQL; Hibernate counts the statements it prepares.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportPerformanceIntegrationTests extends AbstractIntegrationTest {

    private static final int MANY_TREATMENTS = 60;
    private static final int SESSIONS_PER_TREATMENT = 5;
    private static final int PAGE_SIZE = 50;
    private static final Duration MAX_RESPONSE_TIME = Duration.ofSeconds(2);
    /**
     * Token check (account still active), treatments per status, ids of the treatments in the status, sessions per
     * physiotherapist and status, names of the physiotherapists and the READ Report audit entry.
     */
    private static final long SUMMARY_STATEMENTS = 6;
    /**
     * Token check, page of treatments, total, names of the patients, names of the responsibles and the READ entries
     * of every treatment of the page in a single INSERT.
     */
    private static final long LIST_STATEMENTS = 6;
    /**
     * HU-15: token check, treatments per status, ids, sessions, every treatment of the filter, names of the patients,
     * names of the staff (authors, responsibles and the one of the filter in one call), the EXPORT Report entry and the
     * READ entries of every treatment of the PDF in a single INSERT.
     */
    private static final long PDF_STATEMENTS = 9;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User director;
    private Long withOne;
    private Long withMany;

    @BeforeAll
    void insertTwoPhysiotherapistsWithTheirTreatments() {
        director = createUser(Role.OPERATIONS_DIRECTOR, "Dirección Operativa");
        withOne = createUser(Role.PHYSIOTHERAPIST, "Fisioterapeuta con un tratamiento").getId();
        insertTreatment(withOne, "ACTIVE", 10, 1);
        withMany = createUser(Role.PHYSIOTHERAPIST, "Fisioterapeuta con muchos tratamientos").getId();
        String[] statuses = {"ACTIVE", "COMPLETED", "ABANDONED"};
        for (int i = 0; i < MANY_TREATMENTS; i++) {
            insertTreatment(withMany, statuses[i % statuses.length], 10 + i, SESSIONS_PER_TREATMENT);
        }
    }

    @Test
    void theSummaryOfAPhysiotherapistWithSixtyTreatmentsNeedsSixStatements() throws Exception {
        Statistics statistics = statistics();
        // The first request warms up the JIT and the caches; it is not measured
        statements(summaryOf(withMany));

        statistics.clear();
        long start = System.nanoTime();
        mockMvc.perform(authenticated(summaryOf(withMany)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treatments.active").value(MANY_TREATMENTS / 3))
                .andExpect(jsonPath("$.sessions.total").value(MANY_TREATMENTS * SESSIONS_PER_TREATMENT));
        long elapsed = System.nanoTime() - start;

        assertThat(Duration.ofNanos(elapsed)).isLessThan(MAX_RESPONSE_TIME);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(SUMMARY_STATEMENTS);
    }

    @Test
    void theStatementsOfTheSummaryDoNotGrowWithTheTreatmentsNorWithTheFilters() throws Exception {
        long withOneTreatment = statements(summaryOf(withOne));

        assertThat(withOneTreatment).isEqualTo(SUMMARY_STATEMENTS);
        assertThat(statements(summaryOf(withMany))).isEqualTo(withOneTreatment);
        assertThat(statements(summaryOf(withMany).param("status", "COMPLETED"))).isEqualTo(withOneTreatment);
        // Over the whole shared database
        assertThat(statements(get("/api/reports/summary"))).isEqualTo(withOneTreatment);
    }

    @Test
    void aPageOfFiftyTreatmentsNeedsSixStatementsAndAuditsThemInOneInsert() throws Exception {
        Statistics statistics = statistics();
        statements(listOf(withMany));
        long auditEntriesBefore = auditEntries();

        statistics.clear();
        long start = System.nanoTime();
        mockMvc.perform(authenticated(listOf(withMany)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(MANY_TREATMENTS));
        long elapsed = System.nanoTime() - start;

        assertThat(Duration.ofNanos(elapsed)).isLessThan(MAX_RESPONSE_TIME);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(LIST_STATEMENTS);
        // One statement, fifty entries
        assertThat(auditEntries() - auditEntriesBefore).isEqualTo(PAGE_SIZE);
    }

    @Test
    void theStatementsOfTheListDoNotGrowWithTheTreatments() throws Exception {
        long withOneTreatment = statements(listOf(withOne));

        assertThat(withOneTreatment).isEqualTo(LIST_STATEMENTS);
        assertThat(statements(listOf(withMany))).isEqualTo(withOneTreatment);
        // The last page, with fewer treatments than the page size, also counts the total
        assertThat(statements(listOf(withMany).param("page", "1"))).isEqualTo(withOneTreatment);
    }

    @Test
    void aDateRangeDoesNotAddStatements() throws Exception {
        String from = LocalDate.now().minusDays(40).toString();
        String to = LocalDate.now().minusDays(15).toString();

        assertThat(statements(summaryOf(withMany).param("from", from).param("to", to))).isEqualTo(SUMMARY_STATEMENTS);
        assertThat(statements(summaryOf(withMany).param("from", from))).isEqualTo(SUMMARY_STATEMENTS);
        assertThat(statements(listOf(withMany).param("from", from).param("to", to))).isEqualTo(LIST_STATEMENTS);
        assertThat(statements(listOf(withMany).param("to", to))).isEqualTo(LIST_STATEMENTS);
    }

    @Test
    void thePdfOfSixtyTreatmentsNeedsNineStatementsAndAuditsThemInOneInsert() throws Exception {
        Statistics statistics = statistics();
        statements(pdfOf(withMany));
        long auditEntriesBefore = auditEntries();

        statistics.clear();
        long start = System.nanoTime();
        mockMvc.perform(authenticated(pdfOf(withMany))).andExpect(status().isOk());
        long elapsed = System.nanoTime() - start;

        assertThat(Duration.ofNanos(elapsed)).isLessThan(MAX_RESPONSE_TIME);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(PDF_STATEMENTS);
        // Every treatment of the PDF, not one page, in one statement
        assertThat(auditEntries() - auditEntriesBefore).isEqualTo(MANY_TREATMENTS);
    }

    @Test
    void theStatementsOfThePdfDoNotGrowWithTheTreatmentsNorWithTheFilters() throws Exception {
        long withOneTreatment = statements(pdfOf(withOne));

        assertThat(withOneTreatment).isEqualTo(PDF_STATEMENTS);
        assertThat(statements(pdfOf(withMany))).isEqualTo(withOneTreatment);
        assertThat(statements(pdfOf(withMany).param("status", "ABANDONED")
                .param("from", LocalDate.now().minusDays(40).toString())
                .param("to", LocalDate.now().toString()))).isEqualTo(withOneTreatment);
    }

    private long statements(MockHttpServletRequestBuilder request) throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        mockMvc.perform(authenticated(request)).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private static MockHttpServletRequestBuilder summaryOf(Long physiotherapistId) {
        return get("/api/reports/summary").param("physiotherapistId", physiotherapistId.toString());
    }

    private static MockHttpServletRequestBuilder pdfOf(Long physiotherapistId) {
        return get("/api/reports/summary.pdf").param("physiotherapistId", physiotherapistId.toString());
    }

    private static MockHttpServletRequestBuilder listOf(Long physiotherapistId) {
        return get("/api/reports/treatments").param("physiotherapistId", physiotherapistId.toString())
                .param("size", String.valueOf(PAGE_SIZE));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(director, Instant.now()).token());
    }

    /** A treatment of a new patient with the physiotherapist as responsible and author of its sessions. */
    private void insertTreatment(Long physiotherapistId, String status, int daysAgo, int sessions) {
        Long treatmentId = TestTreatments.insert(jdbc, status, LocalDate.now().minusDays(daysAgo));
        TestTreatments.assignResponsible(jdbc, treatmentId, physiotherapistId);
        jdbc.update("""
                INSERT INTO therapy_sessions (treatment_id, responsible_id, sequence_number, session_date, session_time,
                                              session_type, activities_performed, clinical_observations,
                                              patient_instructions, pain_score, progress_status, status)
                SELECT t.id, ?, n, t.start_date + n, TIME '09:30', 'Fisioterapia lumbar mecánica',
                       'Movilización lumbar', 'Buena tolerancia', 'Calor local 15 minutos', 5, 'IMPROVING',
                       CASE WHEN n % 5 = 0 THEN 'NO_SHOW' ELSE 'COMPLETED' END
                FROM treatments t, generate_series(1, ?) AS n
                WHERE t.id = ?""", physiotherapistId, sessions, treatmentId);
    }

    private long auditEntries() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE user_id = ? AND action = 'READ'",
                Long.class, director.getId());
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("reportes-rendimiento-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

}
