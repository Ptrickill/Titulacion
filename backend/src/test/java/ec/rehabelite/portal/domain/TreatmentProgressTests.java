package ec.rehabelite.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.clinicalrecords.TreatmentSummary;
import ec.rehabelite.sessions.SessionSummary;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TreatmentProgressTests {

    private static final Long TREATMENT_ID = 30L;
    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    @Test
    void theProgressComparesTheSessionsThatTookPlaceWithThePlannedOnes() {
        TreatmentProgress progress = TreatmentProgress.of(treatment(7, 12),
                List.of(session(1, 2, "COMPLETED", 6), session(2, 4, "COMPLETED", 5), session(3, 6, "COMPLETED", 5),
                        session(4, 8, "COMPLETED", 4)));

        assertThat(progress.treatmentId()).isEqualTo(TREATMENT_ID);
        assertThat(progress.completedSessions()).isEqualTo(4);
        assertThat(progress.plannedSessions()).isEqualTo(12);
        assertThat(progress.percentage()).isEqualTo(33);
    }

    @Test
    void rescheduledSessionsAndNoShowsDoNotCountAndAreNotPointsOfTheCurve() {
        TreatmentProgress progress = TreatmentProgress.of(treatment(7, 12),
                List.of(session(1, 2, "COMPLETED", 6), session(2, 4, "RESCHEDULED", 9),
                        session(3, 6, "NO_SHOW", 10), session(4, 8, "COMPLETED", 4)));

        assertThat(progress.completedSessions()).isEqualTo(2);
        assertThat(progress.painCurve()).extracting(PainEvolutionPoint::label)
                .containsExactly("Evaluación inicial", "Sesión 1", "Sesión 4");
        assertThat(progress.painCurve()).extracting(PainEvolutionPoint::painScore).containsExactly(7, 6, 4);
    }

    @Test
    void theCurveStartsWithTheInitialEvaluationOnTheFirstDayOfTheTreatment() {
        TreatmentProgress progress = TreatmentProgress.of(treatment(7, 12), List.of(session(1, 2, "COMPLETED", 6)));

        assertThat(progress.painCurve().getFirst())
                .isEqualTo(new PainEvolutionPoint("Evaluación inicial", START, 7, null));
        assertThat(progress.painCurve().get(1))
                .isEqualTo(new PainEvolutionPoint("Sesión 1", START.plusDays(2), 6, 1));
    }

    @Test
    void theCurveKeepsTheChronologicalOrderItReceivesNotTheSequenceNumbers() {
        TreatmentProgress progress = TreatmentProgress.of(treatment(7, 12),
                List.of(session(3, 2, "COMPLETED", 5), session(1, 2, "COMPLETED", 6), session(5, 9, "COMPLETED", 3)));

        assertThat(progress.painCurve()).extracting(PainEvolutionPoint::sequenceNumber)
                .containsExactly(null, 3, 1, 5);
        assertThat(progress.lastPainScore()).isEqualTo(3);
    }

    @Test
    void theReductionIsTheInitialPainScoreMinusTheLastOne() {
        TreatmentProgress progress = TreatmentProgress.of(treatment(7, 12),
                List.of(session(1, 2, "COMPLETED", 6), session(2, 4, "COMPLETED", 4)));

        assertThat(progress.initialPainScore()).isEqualTo(7);
        assertThat(progress.lastPainScore()).isEqualTo(4);
        assertThat(progress.painReduction()).isEqualTo(3);
    }

    @Test
    void theReductionIsNegativeWhenThePainWentUpAndZeroWhenItDidNotChange() {
        assertThat(TreatmentProgress.of(treatment(5, 12), List.of(session(1, 2, "COMPLETED", 8))).painReduction())
                .isEqualTo(-3);
        assertThat(TreatmentProgress.of(treatment(5, 12), List.of(session(1, 2, "COMPLETED", 5))).painReduction())
                .isZero();
    }

    @Test
    void theLastPainScoreIgnoresASessionThatDidNotTakePlace() {
        TreatmentProgress progress = TreatmentProgress.of(treatment(7, 12),
                List.of(session(1, 2, "COMPLETED", 5), session(2, 4, "NO_SHOW", 9)));

        assertThat(progress.lastPainScore()).isEqualTo(5);
        assertThat(progress.painReduction()).isEqualTo(2);
    }

    @Test
    void withoutSessionsThatTookPlaceThereIsOnlyTheInitialEvaluation() {
        TreatmentProgress progress = TreatmentProgress.of(treatment(7, 12),
                List.of(session(1, 2, "RESCHEDULED", 7)));

        assertThat(progress.completedSessions()).isZero();
        assertThat(progress.percentage()).isZero();
        assertThat(progress.lastPainScore()).isNull();
        assertThat(progress.painReduction()).isNull();
        assertThat(progress.painCurve()).singleElement()
                .isEqualTo(new PainEvolutionPoint("Evaluación inicial", START, 7, null));
    }

    @ParameterizedTest(name = "{0} de {1} → {2} %")
    @CsvSource({
        "0,   12,  0",
        "1,   12,  8",
        "4,   12,  33",
        "6,   12,  50",
        "11,  12,  91",
        "12,  12,  100",
        "13,  12,  100",
        "199, 200, 99",
        "1,   0,   0"
    })
    void thePercentageIsRoundedDownAndNeverAboveOneHundred(int completed, int planned, int expected) {
        assertThat(TreatmentProgress.percentageOf(completed, planned)).isEqualTo(expected);
    }

    private static TreatmentSummary treatment(int initialPainScore, int plannedSessions) {
        return new TreatmentSummary(TREATMENT_ID, 8L, true, START, initialPainScore, plannedSessions, "M54.5",
                "Lumbago no especificado");
    }

    private static SessionSummary session(int sequenceNumber, int day, String status, int painScore) {
        return new SessionSummary(100L + sequenceNumber, TREATMENT_ID, sequenceNumber, START.plusDays(day),
                LocalTime.of(9, 30), status, "COMPLETED".equals(status), painScore);
    }

}
