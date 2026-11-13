package ec.rehabelite.sessions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.rehabelite.shared.domain.BusinessRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TherapySessionTests {

    private static final Long TREATMENT_ID = 30L;
    private static final Long PHYSIO_ID = 7L;
    private static final Instant CREATED_AT = Instant.parse("2026-11-04T15:00:00Z");
    private static final Instant CORRECTED_AT = Instant.parse("2026-11-05T15:00:00Z");

    @Test
    void aNewSessionKeepsItsSequenceNumberAndTheResponsibleWhoRegistersIt() {
        TherapySession session = TherapySession.register(TREATMENT_ID, 3, PHYSIO_ID, data(4, "Calor local"),
                CREATED_AT);

        assertThat(session.getTreatmentId()).isEqualTo(TREATMENT_ID);
        assertThat(session.getSequenceNumber()).isEqualTo(3);
        assertThat(session.getResponsibleId()).isEqualTo(PHYSIO_ID);
        assertThat(session.getCreatedBy()).isEqualTo(PHYSIO_ID);
        assertThat(session.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(session.getPainScore()).isEqualTo((short) 4);
        assertThat(session.getUpdatedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11})
    void thePainScoreMustBeBetweenZeroAndTen(int painScore) {
        assertThatThrownBy(() -> TherapySession.register(TREATMENT_ID, 1, PHYSIO_ID, data(painScore, null),
                CREATED_AT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("La EVA debe estar entre 0 y 10.");
    }

    @Test
    void blankInstructionsForThePatientAreStoredAsNull() {
        TherapySession session = TherapySession.register(TREATMENT_ID, 1, PHYSIO_ID, data(4, "   "), CREATED_AT);

        assertThat(session.getPatientInstructions()).isNull();
    }

    @Test
    void aCorrectionReplacesTheEditableFieldsButNotTheSequenceNorTheResponsible() {
        TherapySession session = TherapySession.register(TREATMENT_ID, 2, PHYSIO_ID, data(6, null), CREATED_AT);

        boolean changed = session.correct(data(3, "Hielo 10 minutos"), PHYSIO_ID, CORRECTED_AT);

        assertThat(changed).isTrue();
        assertThat(session.getPainScore()).isEqualTo((short) 3);
        assertThat(session.getPatientInstructions()).isEqualTo("Hielo 10 minutos");
        assertThat(session.getSequenceNumber()).isEqualTo(2);
        assertThat(session.getResponsibleId()).isEqualTo(PHYSIO_ID);
        assertThat(session.getUpdatedBy()).isEqualTo(PHYSIO_ID);
        assertThat(session.getUpdatedAt()).isEqualTo(CORRECTED_AT);
    }

    @Test
    void theSameDataIsNotACorrection() {
        TherapySession session = TherapySession.register(TREATMENT_ID, 1, PHYSIO_ID, data(6, null), CREATED_AT);

        // A blank optional text is the same as none
        assertThat(session.correct(data(6, " "), PHYSIO_ID, CORRECTED_AT)).isFalse();
        assertThat(session.getUpdatedAt()).isNull();
        assertThat(session.getUpdatedBy()).isNull();
    }

    @Test
    void aCorrectionIsValidatedToo() {
        TherapySession session = TherapySession.register(TREATMENT_ID, 1, PHYSIO_ID, data(6, null), CREATED_AT);

        assertThatThrownBy(() -> session.correct(data(11, null), PHYSIO_ID, CORRECTED_AT))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(session.getPainScore()).isEqualTo((short) 6);
    }

    @Test
    void onlyThePhysiotherapistWhoRegisteredTheSessionIsItsAuthor() {
        TherapySession session = TherapySession.register(TREATMENT_ID, 1, PHYSIO_ID, data(6, null), CREATED_AT);

        assertThat(session.isRegisteredBy(PHYSIO_ID)).isTrue();
        assertThat(session.isRegisteredBy(8L)).isFalse();
    }

    static TherapySessionData data(int painScore, String instructions) {
        return new TherapySessionData(LocalDate.of(2026, 11, 4), LocalTime.of(9, 30), "Fisioterapia lumbar",
                "Movilización lumbar", "Buena tolerancia", instructions, painScore, ProgressStatus.IMPROVING,
                SessionStatus.COMPLETED);
    }

}
