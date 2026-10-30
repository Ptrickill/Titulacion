package ec.rehabelite.clinicalrecords.domain;

import static ec.rehabelite.clinicalrecords.domain.ClinicalRecordTests.diagnosis;
import static ec.rehabelite.clinicalrecords.domain.ClinicalRecordTests.history;
import static ec.rehabelite.clinicalrecords.domain.ClinicalRecordTests.treatment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.rehabelite.shared.domain.BusinessRuleException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/** HU-08 rules of a treatment: status transitions and the responsible physiotherapist. */
class TreatmentTests {

    private static final Long PHYSIO_ID = 7L;
    private static final Long OTHER_PHYSIO_ID = 8L;
    private static final Instant OPENED_AT = Instant.parse("2026-10-01T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-30T15:00:00Z");

    @ParameterizedTest
    @CsvSource({"ACTIVE, COMPLETED", "ACTIVE, ABANDONED", "ABANDONED, ACTIVE"})
    void allowedTransitionsKeepTheChangeWithReasonUserAndDate(TreatmentStatus from, TreatmentStatus to) {
        Treatment treatment = treatmentIn(from);

        TreatmentStatusChange change = treatment.changeStatus(to, "  Cumplió los objetivos  ", OTHER_PHYSIO_ID, NOW);

        assertThat(treatment.getStatus()).isEqualTo(to);
        assertThat(change.getTreatment()).isSameAs(treatment);
        assertThat(change.getPreviousStatus()).isEqualTo(from);
        assertThat(change.getNewStatus()).isEqualTo(to);
        assertThat(change.getReason()).isEqualTo("Cumplió los objetivos");
        assertThat(change.getUserId()).isEqualTo(OTHER_PHYSIO_ID);
        assertThat(change.getChangedAt()).isEqualTo(NOW);
        assertThat(treatment.getUpdatedBy()).isEqualTo(OTHER_PHYSIO_ID);
        assertThat(treatment.getUpdatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @CsvSource({
        "ACTIVE, ACTIVE",
        "COMPLETED, ACTIVE",
        "COMPLETED, ABANDONED",
        "COMPLETED, COMPLETED",
        "ABANDONED, COMPLETED",
        "ABANDONED, ABANDONED"})
    void anyOtherTransitionIsRejected(TreatmentStatus from, TreatmentStatus to) {
        Treatment treatment = treatmentIn(from);

        assertThatThrownBy(() -> treatment.changeStatus(to, "Motivo", PHYSIO_ID, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Transición de estado no permitida.");
        assertThat(treatment.getStatus()).isEqualTo(from);
        assertThat(treatment.getUpdatedAt()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void theReasonIsRequired(String reason) {
        Treatment treatment = treatmentIn(TreatmentStatus.ACTIVE);

        assertThatThrownBy(() -> treatment.changeStatus(TreatmentStatus.COMPLETED, reason, PHYSIO_ID, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Treatment.STATUS_CHANGE_REASON_REQUIRED);
        assertThat(treatment.getStatus()).isEqualTo(TreatmentStatus.ACTIVE);
    }

    @Test
    void anAbandonedTreatmentIsNotResumedWhileAnotherOneIsActive() {
        ClinicalRecord record = ClinicalRecord.create(15L, history(), PHYSIO_ID, OPENED_AT);
        Treatment abandoned = record.openTreatment(diagnosis(), treatment(5, 10), PHYSIO_ID, LocalDate.of(2026, 9, 1),
                OPENED_AT);
        abandoned.changeStatus(TreatmentStatus.ABANDONED, "No volvió", PHYSIO_ID, OPENED_AT);
        record.openTreatment(diagnosis(), treatment(4, 8), PHYSIO_ID, LocalDate.of(2026, 10, 1), OPENED_AT);

        assertThatThrownBy(() -> abandoned.changeStatus(TreatmentStatus.ACTIVE, "Retoma", PHYSIO_ID, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(ClinicalRecord.ACTIVE_TREATMENT_EXISTS);
        assertThat(abandoned.getStatus()).isEqualTo(TreatmentStatus.ABANDONED);
    }

    @Test
    void allowedNextStatusesFollowTheTransitionRules() {
        assertThat(treatmentIn(TreatmentStatus.ACTIVE).allowedNextStatuses())
                .containsExactly(TreatmentStatus.COMPLETED, TreatmentStatus.ABANDONED);
        assertThat(treatmentIn(TreatmentStatus.ABANDONED).allowedNextStatuses())
                .containsExactly(TreatmentStatus.ACTIVE);
        assertThat(treatmentIn(TreatmentStatus.COMPLETED).allowedNextStatuses()).isEmpty();
    }

    @Test
    void reassigningEndsTheCurrentAssignmentAndStartsANewOne() {
        Treatment treatment = treatmentIn(TreatmentStatus.ACTIVE);
        ResponsibleAssignment previous = treatment.currentResponsible().orElseThrow();

        ResponsibleAssignment next = treatment.reassignResponsible(OTHER_PHYSIO_ID, PHYSIO_ID, NOW).orElseThrow();

        assertThat(previous.getValidTo()).isEqualTo(NOW);
        assertThat(previous.isCurrent()).isFalse();
        assertThat(next.getTreatment()).isSameAs(treatment);
        assertThat(next.getPhysiotherapistId()).isEqualTo(OTHER_PHYSIO_ID);
        assertThat(next.getValidFrom()).isEqualTo(NOW);
        assertThat(next.isCurrent()).isTrue();
        assertThat(treatment.getUpdatedBy()).isEqualTo(PHYSIO_ID);
        assertThat(treatment.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void theCurrentResponsibleChangesNothing() {
        Treatment treatment = treatmentIn(TreatmentStatus.ACTIVE);

        assertThat(treatment.reassignResponsible(PHYSIO_ID, PHYSIO_ID, NOW)).isEmpty();

        assertThat(treatment.currentResponsible()).hasValueSatisfying(
                assignment -> assertThat(assignment.getValidTo()).isNull());
        assertThat(treatment.getUpdatedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "ABANDONED"})
    void theResponsibleOfAClosedTreatmentDoesNotChange(TreatmentStatus status) {
        Treatment treatment = treatmentIn(status);

        assertThatThrownBy(() -> treatment.reassignResponsible(OTHER_PHYSIO_ID, PHYSIO_ID, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Treatment.RESPONSIBLE_CHANGE_NEEDS_ACTIVE_TREATMENT);
        assertThat(treatment.currentResponsible()).hasValueSatisfying(
                assignment -> assertThat(assignment.getPhysiotherapistId()).isEqualTo(PHYSIO_ID));
    }

    private static Treatment treatmentIn(TreatmentStatus status) {
        ClinicalRecord record = ClinicalRecord.create(15L, history(), PHYSIO_ID, OPENED_AT);
        Treatment treatment = record.openTreatment(diagnosis(), treatment(5, 10), PHYSIO_ID,
                LocalDate.of(2026, 10, 1), OPENED_AT);
        ReflectionTestUtils.setField(treatment, "status", status);
        return treatment;
    }

}
