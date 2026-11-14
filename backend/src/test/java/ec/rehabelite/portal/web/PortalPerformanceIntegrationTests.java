package ec.rehabelite.portal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
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

/**
 * HU-12 and HU-13: the portal answers with a fixed number of SQL statements, without N+1. The list costs the same
 * with one treatment as with five (each with its own responsible and sessions), and the progress, the sessions and
 * the last instructions the same with one session as with a hundred. Data is inserted with SQL; Hibernate counts the
 * statements it prepares.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalPerformanceIntegrationTests extends AbstractIntegrationTest {

    private static final int TREATMENTS = 5;
    private static final int SESSIONS = 100;
    private static final Duration MAX_RESPONSE_TIME = Duration.ofSeconds(2);
    /**
     * Reads of the list: token check (account still active), patient of the account, treatments with their
     * diagnosis, current responsibles, names of the responsibles and completed sessions per treatment.
     */
    private static final long LIST_READS = 6;
    /** The READ entries of every treatment listed go in a single INSERT. */
    private static final long LIST_AUDIT_INSERTS = 1;
    /** Token check, patient of the account, treatment, sessions and the READ audit entry. */
    private static final long PROGRESS_STATEMENTS = 5;
    /** Token check, patient of the account, treatment, sessions, names of the responsibles and the audit entry. */
    private static final long SESSIONS_STATEMENTS = 6;
    /** Token check, patient of the account, treatment, one session, the name of its responsible and the audit. */
    private static final long INSTRUCTIONS_STATEMENTS = 6;

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

    private String tokenWithFive;
    private Long patientWithFiveUserId;
    private String tokenWithOne;
    private Long treatmentWith100;
    private Long treatmentWithOne;

    @BeforeAll
    void insertTwoPatientsWithTheirTreatments() {
        User withFive = createUser(Role.PATIENT, "Paciente con cinco tratamientos");
        Long record = TestTreatments.insertRecord(jdbc, insertLinkedPatient(withFive));
        // Four finished treatments, each with its own responsible and 10 sessions, and the active one with 100
        for (int i = 1; i < TREATMENTS; i++) {
            Long finished = TestTreatments.insertFor(jdbc, record, "COMPLETED",
                    LocalDate.now().minusDays(200L * (i + 1)), 8, 10);
            insertSessions(finished, createUser(Role.PHYSIOTHERAPIST, "Fisioterapeuta " + i).getId(), 10);
        }
        treatmentWith100 = TestTreatments.insertFor(jdbc, record, "ACTIVE", LocalDate.now().minusDays(SESSIONS + 1),
                7, 120);
        insertSessions(treatmentWith100, createUser(Role.PHYSIOTHERAPIST, "Fisioterapeuta activo").getId(), SESSIONS);
        tokenWithFive = tokenService.issue(withFive, Instant.now()).token();
        patientWithFiveUserId = withFive.getId();

        User withOne = createUser(Role.PATIENT, "Paciente con un tratamiento");
        treatmentWithOne = TestTreatments.insertFor(jdbc,
                TestTreatments.insertRecord(jdbc, insertLinkedPatient(withOne)), "ACTIVE",
                LocalDate.now().minusDays(10), 7, 12);
        insertSessions(treatmentWithOne, createUser(Role.PHYSIOTHERAPIST, "Fisioterapeuta único").getId(), 1);
        tokenWithOne = tokenService.issue(withOne, Instant.now()).token();
    }

    @Test
    void theListReadsWithSixStatementsAndAuditsEveryTreatmentInOneInsert() throws Exception {
        Statistics statistics = statistics();
        // The first request warms up the JIT and the caches; it is not measured
        list(tokenWithFive);
        long auditEntriesBefore = auditEntries();

        statistics.clear();
        long start = System.nanoTime();
        mockMvc.perform(get("/api/portal/treatments").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithFive))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(TREATMENTS))
                .andExpect(jsonPath("$[0].completedSessions").value(SESSIONS - SESSIONS / 10))
                .andExpect(jsonPath("$[0].responsibleName").value("Fisioterapeuta activo"))
                .andExpect(jsonPath("$[1].completedSessions").value(9));
        long elapsed = System.nanoTime() - start;

        assertThat(Duration.ofNanos(elapsed)).isLessThan(MAX_RESPONSE_TIME);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(LIST_READS + LIST_AUDIT_INSERTS);
        // One statement, five entries
        assertThat(auditEntries() - auditEntriesBefore).isEqualTo(TREATMENTS);
    }

    @Test
    void theStatementsOfTheListDoNotGrowWithTheTreatments() throws Exception {
        assertThat(list(tokenWithFive)).isEqualTo(list(tokenWithOne));
    }

    @Test
    void theProgressOfATreatmentWithOneHundredSessionsNeedsFiveStatements() throws Exception {
        Statistics statistics = statistics();
        progress(tokenWithFive, treatmentWith100);

        statistics.clear();
        long start = System.nanoTime();
        mockMvc.perform(get("/api/portal/treatments/{id}/progress", treatmentWith100)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithFive))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedSessions").value(SESSIONS - SESSIONS / 10))
                .andExpect(jsonPath("$.painCurve.length()").value(1 + SESSIONS - SESSIONS / 10))
                .andExpect(jsonPath("$.percentage").value(75));
        long elapsed = System.nanoTime() - start;

        assertThat(Duration.ofNanos(elapsed)).isLessThan(MAX_RESPONSE_TIME);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(PROGRESS_STATEMENTS);
    }

    @Test
    void theStatementsOfTheProgressDoNotGrowWithTheSessions() throws Exception {
        assertThat(progress(tokenWithFive, treatmentWith100)).isEqualTo(progress(tokenWithOne, treatmentWithOne));
    }

    @Test
    void theSessionsOfATreatmentWithOneHundredSessionsNeedSixStatements() throws Exception {
        Statistics statistics = statistics();
        read(tokenWithFive, treatmentWith100, "sessions");

        statistics.clear();
        long start = System.nanoTime();
        mockMvc.perform(get("/api/portal/treatments/{id}/sessions", treatmentWith100)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithFive))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SESSIONS))
                .andExpect(jsonPath("$[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$[99].responsibleName").value("Fisioterapeuta activo"));
        long elapsed = System.nanoTime() - start;

        assertThat(Duration.ofNanos(elapsed)).isLessThan(MAX_RESPONSE_TIME);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(SESSIONS_STATEMENTS);
    }

    @Test
    void theStatementsOfTheSessionsDoNotGrowWithTheSessions() throws Exception {
        assertThat(read(tokenWithFive, treatmentWith100, "sessions"))
                .isEqualTo(read(tokenWithOne, treatmentWithOne, "sessions"));
    }

    @Test
    void theLastInstructionsOfATreatmentWithOneHundredSessionsNeedSixStatements() throws Exception {
        Statistics statistics = statistics();
        read(tokenWithFive, treatmentWith100, "instructions");

        statistics.clear();
        long start = System.nanoTime();
        mockMvc.perform(get("/api/portal/treatments/{id}/instructions", treatmentWith100)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithFive))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sequenceNumber").value(SESSIONS))
                .andExpect(jsonPath("$.responsibleName").value("Fisioterapeuta activo"));
        long elapsed = System.nanoTime() - start;

        assertThat(Duration.ofNanos(elapsed)).isLessThan(MAX_RESPONSE_TIME);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(INSTRUCTIONS_STATEMENTS);
    }

    @Test
    void theStatementsOfTheLastInstructionsDoNotGrowWithTheSessions() throws Exception {
        assertThat(read(tokenWithFive, treatmentWith100, "instructions"))
                .isEqualTo(read(tokenWithOne, treatmentWithOne, "instructions"));
    }

    /** Statements prepared to read the sessions or the instructions of the treatment. */
    private long read(String token, Long treatmentId, String resource) throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        mockMvc.perform(get("/api/portal/treatments/{id}/" + resource, treatmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    /** Statements prepared to list the treatments of the patient of the token. */
    private long list(String token) throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        mockMvc.perform(get("/api/portal/treatments").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    /** Statements prepared to read the progress of the treatment. */
    private long progress(String token, Long treatmentId) throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        mockMvc.perform(get("/api/portal/treatments/{id}/progress", treatmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    /** The physiotherapist becomes the responsible and registers one session a day; every tenth was rescheduled. */
    private void insertSessions(Long treatmentId, Long physiotherapistId, int sessions) {
        TestTreatments.assignResponsible(jdbc, treatmentId, physiotherapistId);
        jdbc.update("""
                INSERT INTO therapy_sessions (treatment_id, responsible_id, sequence_number, session_date, session_time,
                                              session_type, activities_performed, clinical_observations,
                                              patient_instructions, pain_score, progress_status, status)
                SELECT t.id, ?, n, t.start_date + n, TIME '09:30', 'Fisioterapia lumbar mecánica',
                       'Movilización lumbar', 'Buena tolerancia', 'Calor local 15 minutos', 10 - n % 11, 'IMPROVING',
                       CASE WHEN n % 10 = 0 THEN 'RESCHEDULED' ELSE 'COMPLETED' END
                FROM treatments t, generate_series(1, ?) AS n
                WHERE t.id = ?""", physiotherapistId, sessions, treatmentId);
    }

    /** READ entries of the patient with five treatments. */
    private long auditEntries() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE user_id = ? AND action = 'READ'",
                Long.class, patientWithFiveUserId);
    }

    private Long insertLinkedPatient(User account) {
        Long patientId = TestPatients.insert(jdbc, "Paciente", "Del Portal");
        jdbc.update("UPDATE patients SET portal_user_id = ? WHERE id = ?", account.getId(), patientId);
        return patientId;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private User createUser(Role role, String fullName) {
        return userRepository.save(User.create("portal-rendimiento-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, role, null, Instant.now()));
    }

}
