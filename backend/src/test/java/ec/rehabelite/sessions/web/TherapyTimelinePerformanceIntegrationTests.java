package ec.rehabelite.sessions.web;

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

/**
 * HU-11: the timeline of a treatment with 100 sessions (registered by four physiotherapists, a tenth of them not
 * completed) answers in under 2 seconds and with a bounded number of SQL statements, the same as with one session.
 * Sessions are inserted with SQL; Hibernate counts the statements it prepares.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TherapyTimelinePerformanceIntegrationTests extends AbstractIntegrationTest {

    private static final int SESSIONS = 100;
    private static final int PHYSIOTHERAPISTS = 4;
    private static final Duration MAX_RESPONSE_TIME = Duration.ofSeconds(2);
    /**
     * Token check (account still active), treatment with record and diagnosis, sessions, completed count, names of
     * the responsibles and the READ audit entry.
     */
    private static final long MAX_STATEMENTS = 6;

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

    private String physioToken;
    private Long[] physioIds;
    private Long treatmentWith100;

    @BeforeAll
    void insertATreatmentWithItsSessions() {
        physioIds = new Long[PHYSIOTHERAPISTS];
        for (int i = 0; i < PHYSIOTHERAPISTS; i++) {
            User physio = createPhysiotherapist("Fisioterapeuta " + i);
            physioIds[i] = physio.getId();
            if (i == 0) {
                physioToken = tokenService.issue(physio, Instant.now()).token();
            }
        }
        treatmentWith100 = insertTreatmentWithSessions(SESSIONS);
    }

    @Test
    void aTreatmentWithOneHundredSessionsAnswersInUnderTwoSecondsWithABoundedNumberOfStatements() throws Exception {
        Statistics statistics = statistics();
        // The first request warms up the JIT and the caches; it is not measured
        read(treatmentWith100);

        statistics.clear();
        long start = System.nanoTime();
        mockMvc.perform(get("/api/treatments/{id}/timeline", treatmentWith100)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + physioToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(SESSIONS))
                .andExpect(jsonPath("$.painCurve.length()").value(1 + SESSIONS - SESSIONS / 10))
                .andExpect(jsonPath("$.sessions[99].responsible.fullName").value("Fisioterapeuta 3"));
        long elapsed = System.nanoTime() - start;

        assertThat(Duration.ofNanos(elapsed)).isLessThan(MAX_RESPONSE_TIME);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(MAX_STATEMENTS);
    }

    @Test
    void theNumberOfStatementsDoesNotGrowWithTheSessions() throws Exception {
        Long withOneSession = insertTreatmentWithSessions(1);

        assertThat(read(treatmentWith100)).isEqualTo(read(withOneSession));
    }

    /** Statements prepared to read the timeline of the treatment. */
    private long read(Long treatmentId) throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        mockMvc.perform(get("/api/treatments/{id}/timeline", treatmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + physioToken))
                .andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    /**
     * A treatment that started {@code sessions} days ago with one session a day; the responsibles take turns and
     * every tenth session was rescheduled.
     */
    private Long insertTreatmentWithSessions(int sessions) {
        Long treatmentId = TestTreatments.insert(jdbc, "ACTIVE", LocalDate.now().minusDays(sessions + 1));
        jdbc.update("""
                INSERT INTO therapy_sessions (treatment_id, responsible_id, sequence_number, session_date, session_time,
                                              session_type, activities_performed, clinical_observations,
                                              patient_instructions, pain_score, progress_status, status)
                SELECT t.id, (ARRAY[?, ?, ?, ?]::bigint[])[1 + (n - 1) % 4], n, t.start_date + n, TIME '09:30',
                       'Fisioterapia lumbar mecánica', 'Movilización lumbar', 'Buena tolerancia',
                       'Calor local 15 minutos', 10 - n % 11, 'IMPROVING',
                       CASE WHEN n % 10 = 0 THEN 'RESCHEDULED' ELSE 'COMPLETED' END
                FROM treatments t, generate_series(1, ?) AS n
                WHERE t.id = ?""", physioIds[0], physioIds[1], physioIds[2], physioIds[3], sessions, treatmentId);
        return treatmentId;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private User createPhysiotherapist(String fullName) {
        return userRepository.save(User.create("linea-rendimiento-" + UUID.randomUUID() + "@rehabelite.test",
                "not-a-real-hash", fullName, Role.PHYSIOTHERAPIST, null, Instant.now()));
    }

}
