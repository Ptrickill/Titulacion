package ec.rehabelite.sessions.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestSessions;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.sessions.SessionSummary;
import ec.rehabelite.sessions.TherapySessionQueryPort;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** HU-12: what the public port of sessions tells other modules about the sessions of a treatment. */
class TherapySessionQueryPortIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private TherapySessionQueryPort sessionQuery;

    @Autowired
    private JdbcTemplate jdbc;

    private Long physioId;
    private Long treatmentId;
    private LocalDate start;

    @BeforeEach
    void setUp() {
        physioId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, 'not-a-real-hash', 'Fisioterapeuta de prueba', 'PHYSIOTHERAPIST')
                RETURNING id""", Long.class, "puerto-" + UUID.randomUUID() + "@rehabelite.test");
        treatmentId = TestTreatments.insertActive(jdbc);
        start = LocalDate.now().minusDays(30);
        TestSessions.insert(jdbc, treatmentId, physioId, 1, start.plusDays(2), "10:00", 6, "COMPLETED");
        TestSessions.insert(jdbc, treatmentId, physioId, 2, start.plusDays(5), "08:00", 6, "RESCHEDULED");
        TestSessions.insert(jdbc, treatmentId, physioId, 3, start.plusDays(2), "08:00", 5, "COMPLETED");
        TestSessions.insert(jdbc, treatmentId, physioId, 4, start.plusDays(5), "08:00", 5, "NO_SHOW");
    }

    @Test
    void theSessionsComeInChronologicalOrderWhateverTheirStatus() {
        List<SessionSummary> sessions = sessionQuery.chronological(treatmentId);

        // Same date and time for 2 and 4: the sequence number decides
        assertThat(sessions).extracting(SessionSummary::sequenceNumber).containsExactly(3, 1, 2, 4);
        assertThat(sessions).extracting(SessionSummary::status)
                .containsExactly("COMPLETED", "COMPLETED", "RESCHEDULED", "NO_SHOW");
        assertThat(sessions).extracting(SessionSummary::completed).containsExactly(true, true, false, false);
        assertThat(sessions.getFirst()).satisfies(session -> {
            assertThat(session.id()).isNotNull();
            assertThat(session.treatmentId()).isEqualTo(treatmentId);
            assertThat(session.sessionDate()).isEqualTo(start.plusDays(2));
            assertThat(session.sessionTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(session.painScore()).isEqualTo(5);
        });
    }

    @Test
    void aTreatmentWithoutSessionsHasNone() {
        assertThat(sessionQuery.chronological(TestTreatments.insertActive(jdbc))).isEmpty();
    }

    @Test
    void theCompletedCountsHaveEveryTreatmentAskedForAndOnlyCountTheSessionsThatTookPlace() {
        Long withoutSessions = TestTreatments.insertActive(jdbc);
        Long onlyNoShows = TestTreatments.insertActive(jdbc);
        TestSessions.insert(jdbc, onlyNoShows, physioId, 1, start.plusDays(3), "09:00", 7, "NO_SHOW");

        Map<Long, Long> counts = sessionQuery.completedCounts(List.of(treatmentId, withoutSessions, onlyNoShows));

        assertThat(counts).containsOnly(Map.entry(treatmentId, 2L), Map.entry(withoutSessions, 0L),
                Map.entry(onlyNoShows, 0L));
        assertThat(sessionQuery.completedCounts(List.of())).isEmpty();
    }

}
