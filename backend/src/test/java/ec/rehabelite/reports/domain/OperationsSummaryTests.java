package ec.rehabelite.reports.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.reports.domain.OperationsSummary.PhysiotherapistSessions;
import ec.rehabelite.reports.domain.OperationsSummary.SessionCounts;
import ec.rehabelite.reports.domain.OperationsSummary.TreatmentCounts;
import ec.rehabelite.sessions.SessionCount;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** HU-14: how the indicators of the operations report add up the counts. */
class OperationsSummaryTests {

    private static final Long VALERIA = 3L;
    private static final Long MATEO = 4L;
    private static final Long JULIAN = 5L;
    private static final Map<Long, String> NAMES = Map.of(VALERIA, "Valeria Andrade", MATEO, "Mateo Ríos",
            JULIAN, "Julián Rivas");

    @Test
    void theTreatmentsComeFromTheCountsPerStatus() {
        OperationsSummary summary = OperationsSummary.of(Map.of("ACTIVE", 5L, "COMPLETED", 1L, "ABANDONED", 1L),
                List.of(), Map.of());

        assertThat(summary.treatments()).isEqualTo(new TreatmentCounts(5, 1, 1));
    }

    @Test
    void aStatusWithoutTreatmentsCountsAsZero() {
        OperationsSummary summary = OperationsSummary.of(Map.of("COMPLETED", 2L), List.of(), Map.of());

        assertThat(summary.treatments()).isEqualTo(new TreatmentCounts(0, 2, 0));
    }

    @Test
    void theSessionsAreAddedUpPerStatusAndInTotal() {
        OperationsSummary summary = OperationsSummary.of(Map.of(), List.of(
                new SessionCount(VALERIA, "COMPLETED", 10), new SessionCount(VALERIA, "RESCHEDULED", 2),
                new SessionCount(MATEO, "COMPLETED", 4), new SessionCount(MATEO, "NO_SHOW", 1)), NAMES);

        assertThat(summary.sessions()).isEqualTo(new SessionCounts(17, 14, 2, 1));
    }

    @Test
    void eachPhysiotherapistHasAllTheSessionsTheyRegisteredFromTheMostToTheFewest() {
        OperationsSummary summary = OperationsSummary.of(Map.of(), List.of(
                new SessionCount(MATEO, "COMPLETED", 4), new SessionCount(VALERIA, "COMPLETED", 10),
                new SessionCount(VALERIA, "NO_SHOW", 2), new SessionCount(MATEO, "RESCHEDULED", 1),
                new SessionCount(JULIAN, "COMPLETED", 5)), NAMES);

        assertThat(summary.sessionsByPhysiotherapist()).containsExactly(
                new PhysiotherapistSessions(VALERIA, "Valeria Andrade", 12),
                new PhysiotherapistSessions(JULIAN, "Julián Rivas", 5),
                new PhysiotherapistSessions(MATEO, "Mateo Ríos", 5));
    }

    @Test
    void theAverageIsTheTotalOverThePhysiotherapistsWithSessionsWithOneDecimal() {
        // 28 sessions among 5 physiotherapists, as in the prototype
        OperationsSummary summary = OperationsSummary.of(Map.of(), List.of(
                new SessionCount(1L, "COMPLETED", 12), new SessionCount(2L, "COMPLETED", 6),
                new SessionCount(3L, "COMPLETED", 5), new SessionCount(4L, "COMPLETED", 3),
                new SessionCount(5L, "COMPLETED", 2)), Map.of());

        assertThat(summary.averageSessionsPerPhysiotherapist()).isEqualByComparingTo("5.6");
        assertThat(summary.averageSessionsPerPhysiotherapist().scale()).isEqualTo(1);
    }

    @Test
    void theAverageIsRoundedHalfUp() {
        // 5 / 3 = 1.666..., 4 / 3 = 1.333..., 10 / 4 = 2.5 and 7 / 2 = 3.5
        assertThat(averageOf(2, 2, 1)).isEqualByComparingTo("1.7");
        assertThat(averageOf(2, 1, 1)).isEqualByComparingTo("1.3");
        assertThat(averageOf(4, 3, 2, 1)).isEqualByComparingTo("2.5");
        assertThat(averageOf(4, 3)).isEqualByComparingTo("3.5");
        assertThat(averageOf(6)).isEqualByComparingTo("6.0");
    }

    @Test
    void withoutSessionsTheAverageIsZeroAndThereAreNoPhysiotherapists() {
        OperationsSummary summary = OperationsSummary.of(Map.of("ACTIVE", 3L), List.of(), Map.of());

        assertThat(summary.sessions()).isEqualTo(new SessionCounts(0, 0, 0, 0));
        assertThat(summary.sessionsByPhysiotherapist()).isEmpty();
        assertThat(summary.averageSessionsPerPhysiotherapist()).isEqualTo(new BigDecimal("0.0"));
    }

    @Test
    void aPhysiotherapistWhoseAccountNoLongerExistsHasNoNameAndGoesLastAmongTies() {
        OperationsSummary summary = OperationsSummary.of(Map.of(), List.of(
                new SessionCount(99L, "COMPLETED", 3), new SessionCount(VALERIA, "COMPLETED", 3)), NAMES);

        assertThat(summary.sessionsByPhysiotherapist()).containsExactly(
                new PhysiotherapistSessions(VALERIA, "Valeria Andrade", 3), new PhysiotherapistSessions(99L, null, 3));
    }

    /** The average of one physiotherapist per value, each with that number of sessions. */
    private static BigDecimal averageOf(long... sessions) {
        List<SessionCount> counts = new ArrayList<>();
        for (int i = 0; i < sessions.length; i++) {
            counts.add(new SessionCount((long) i, "COMPLETED", sessions[i]));
        }
        return OperationsSummary.of(Map.of(), counts, Map.of()).averageSessionsPerPhysiotherapist();
    }

}
