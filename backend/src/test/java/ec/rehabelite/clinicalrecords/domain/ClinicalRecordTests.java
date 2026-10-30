package ec.rehabelite.clinicalrecords.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.rehabelite.shared.domain.BusinessRuleException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class ClinicalRecordTests {

    private static final Long PHYSIO_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-10-30T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    @Test
    void firstTreatmentIsActiveWithTheOpeningPhysiotherapistAsCurrentResponsible() {
        ClinicalRecord record = ClinicalRecord.create(15L, history(), PHYSIO_ID, NOW);

        Treatment treatment = record.openTreatment(diagnosis(), treatment(7, 12), PHYSIO_ID, TODAY, NOW);

        assertThat(record.getTreatments()).containsExactly(treatment);
        assertThat(record.getPatientId()).isEqualTo(15L);
        assertThat(record.getHabits()).isEqualTo("No fuma");
        assertThat(treatment.getStatus()).isEqualTo(TreatmentStatus.ACTIVE);
        assertThat(treatment.getStartDate()).isEqualTo(TODAY);
        assertThat(treatment.getInitialPainScore()).isEqualTo((short) 7);
        assertThat(treatment.getChiefComplaint()).isEqualTo("Dolor lumbar");
        assertThat(treatment.getCreatedBy()).isEqualTo(PHYSIO_ID);
        assertThat(treatment.currentResponsible()).hasValueSatisfying(assignment -> {
            assertThat(assignment.getPhysiotherapistId()).isEqualTo(PHYSIO_ID);
            assertThat(assignment.getValidFrom()).isEqualTo(NOW);
            assertThat(assignment.isCurrent()).isTrue();
        });
    }

    @Test
    void onlyOneTreatmentCanBeActive() {
        ClinicalRecord record = ClinicalRecord.create(15L, history(), PHYSIO_ID, NOW);
        record.openTreatment(diagnosis(), treatment(5, 10), PHYSIO_ID, TODAY, NOW);

        assertThatThrownBy(() -> record.openTreatment(diagnosis(), treatment(5, 10), PHYSIO_ID, TODAY, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(ClinicalRecord.ACTIVE_TREATMENT_EXISTS);
        assertThat(record.getTreatments()).hasSize(1);
    }

    @Test
    void aNewTreatmentCanStartOnceThePreviousOneIsClosed() {
        ClinicalRecord record = ClinicalRecord.create(15L, history(), PHYSIO_ID, NOW);
        Treatment first = record.openTreatment(diagnosis(), treatment(5, 10), PHYSIO_ID, TODAY, NOW);
        ReflectionTestUtils.setField(first, "status", TreatmentStatus.COMPLETED);

        Treatment second = record.openTreatment(diagnosis(), treatment(3, 6), 8L, TODAY, NOW);

        assertThat(record.getTreatments()).containsExactly(first, second);
        assertThat(second.currentResponsible()).hasValueSatisfying(
                assignment -> assertThat(assignment.getPhysiotherapistId()).isEqualTo(8L));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11})
    void painScoreMustBeBetweenZeroAndTen(int painScore) {
        ClinicalRecord record = ClinicalRecord.create(15L, history(), PHYSIO_ID, NOW);

        assertThatThrownBy(() -> record.openTreatment(diagnosis(), treatment(painScore, 10), PHYSIO_ID, TODAY, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Treatment.PAIN_SCORE_OUT_OF_RANGE);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3})
    void plannedSessionsMustBePositive(int plannedSessions) {
        ClinicalRecord record = ClinicalRecord.create(15L, history(), PHYSIO_ID, NOW);

        assertThatThrownBy(() -> record.openTreatment(diagnosis(), treatment(5, plannedSessions), PHYSIO_ID, TODAY,
                NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Treatment.PLANNED_SESSIONS_NOT_POSITIVE);
    }

    static ClinicalHistoryData history() {
        return new ClinicalHistoryData(" No fuma ", "Camina", "Ninguna", "Ninguna", "Ninguno", "Ninguno",
                "Ninguno");
    }

    static TreatmentData treatment(int painScore, int plannedSessions) {
        return new TreatmentData(" Dolor lumbar ", "Tres semanas", "Dr. Andrade", "Radiografía",
                Severity.MODERATE, "Punzante", "Lumbar", painScore, "Intermitente", "3 por semana",
                "Reducir el dolor", plannedSessions);
    }

    static Icd10Diagnosis diagnosis() {
        Icd10Diagnosis diagnosis = BeanUtils.instantiateClass(Icd10Diagnosis.class);
        ReflectionTestUtils.setField(diagnosis, "code", "M54.5");
        ReflectionTestUtils.setField(diagnosis, "description", "Lumbago no especificado");
        return diagnosis;
    }

}
