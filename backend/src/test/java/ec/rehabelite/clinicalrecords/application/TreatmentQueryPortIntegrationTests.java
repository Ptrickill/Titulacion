package ec.rehabelite.clinicalrecords.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestClinicalText;
import ec.rehabelite.TestPatients;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.clinicalrecords.TreatmentFilter;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-12 and HU-14: what the public port of clinicalrecords tells other modules about the treatments of a patient and
 * about the treatments of the operations reports.
 */
class TreatmentQueryPortIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private TreatmentQueryPort treatmentQuery;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theActiveTreatmentComesFirstThenTheOthersFromTheMostRecentToTheOldestWithTheirCurrentResponsible() {
        LocalDate today = LocalDate.now();
        Long patientId = TestPatients.insert(jdbc, "Rosa Elena", "Ortiz Vega");
        Long record = TestTreatments.insertRecord(jdbc, patientId);
        Long oldest = TestTreatments.insertFor(jdbc, record, "COMPLETED", today.minusDays(300), 8, 10);
        Long active = TestTreatments.insertFor(jdbc, record, "ACTIVE", today.minusDays(30), 7, 12);
        Long abandoned = TestTreatments.insertFor(jdbc, record, "ABANDONED", today.minusDays(100), 6, 8);
        Long sameDayAsAbandoned = TestTreatments.insertFor(jdbc, record, "COMPLETED", today.minusDays(100), 5, 6);
        Long valeria = insertPhysiotherapist();
        Long mateo = insertPhysiotherapist();
        // A closed assignment is history: only the current one is the responsible
        jdbc.update("""
                INSERT INTO responsible_assignments (treatment_id, physiotherapist_id, valid_from, valid_to)
                VALUES (?, ?, now() - INTERVAL '2 days', now() - INTERVAL '1 day')""", active, mateo);
        TestTreatments.assignResponsible(jdbc, active, valeria);
        TestTreatments.assignResponsible(jdbc, oldest, mateo);

        List<PatientTreatment> treatments = treatmentQuery.findByPatient(patientId);

        assertThat(treatments).extracting(PatientTreatment::id)
                .containsExactly(active, sameDayAsAbandoned, abandoned, oldest);
        assertThat(treatments).extracting(PatientTreatment::status)
                .containsExactly("ACTIVE", "COMPLETED", "ABANDONED", "COMPLETED");
        assertThat(treatments).extracting(PatientTreatment::active).containsExactly(true, false, false, false);
        assertThat(treatments).extracting(PatientTreatment::responsibleId)
                .containsExactly(valeria, null, null, mateo);
        assertThat(treatments.getFirst()).isEqualTo(new PatientTreatment(active, patientId, "ACTIVE", true,
                today.minusDays(30), 7, 12, "M54.5", "Lumbago no especificado", valeria));
    }

    @Test
    void aPatientWithoutClinicalRecordOrWithoutTreatmentsHasNone() {
        Long withoutRecord = TestPatients.insert(jdbc, "Ana Lucía", "Mora Ruiz");
        Long withEmptyRecord = TestPatients.insert(jdbc, "Luis Alberto", "Quishpe Tipán");
        TestTreatments.insertRecord(jdbc, withEmptyRecord);

        assertThat(treatmentQuery.findByPatient(withoutRecord)).isEmpty();
        assertThat(treatmentQuery.findByPatient(withEmptyRecord)).isEmpty();
        assertThat(treatmentQuery.findByPatient(999_999_999L)).isEmpty();
    }

    @Test
    void theTreatmentsOfOtherPatientsAreNeverListed() {
        Long patientId = TestPatients.insert(jdbc, "Daniela Sofía", "Zambrano Loor");
        Long own = TestTreatments.insertFor(jdbc, TestTreatments.insertRecord(jdbc, patientId), "ACTIVE",
                LocalDate.now().minusDays(10), 7, 12);
        TestTreatments.insertActive(jdbc);

        assertThat(treatmentQuery.findByPatient(patientId)).extracting(PatientTreatment::id).containsExactly(own);
    }

    // ----- operations reports (HU-14) -----

    @Test
    void theCountsPerStatusOfAResponsibleOnlyTakeTheTreatmentsTheyAreCurrentlyResponsibleFor() {
        Long physio = insertPhysiotherapist();
        Long other = insertPhysiotherapist();
        assignedTo(physio, "ACTIVE", 10);
        assignedTo(physio, "COMPLETED", 100);
        assignedTo(physio, "COMPLETED", 200);
        assignedTo(physio, "ABANDONED", 50);
        assignedTo(other, "ACTIVE", 10);
        // Handed over to the other physiotherapist: no longer counts for the first one
        Long handedOver = assignedTo(other, "ACTIVE", 20);
        closedAssignment(handedOver, physio);

        assertThat(treatmentQuery.countByStatus(new TreatmentFilter(null, physio)))
                .containsExactly(Map.entry("ACTIVE", 1L), Map.entry("COMPLETED", 2L), Map.entry("ABANDONED", 1L));
        assertThat(treatmentQuery.countByStatus(new TreatmentFilter("COMPLETED", physio)))
                .containsExactly(Map.entry("ACTIVE", 0L), Map.entry("COMPLETED", 2L), Map.entry("ABANDONED", 0L));
        assertThat(treatmentQuery.countByStatus(new TreatmentFilter(null, other)))
                .containsExactly(Map.entry("ACTIVE", 2L), Map.entry("COMPLETED", 0L), Map.entry("ABANDONED", 0L));
        assertThat(treatmentQuery.countByStatus(new TreatmentFilter(null, 999_999_999L)).values())
                .containsOnly(0L);
    }

    @Test
    void theCountsWithoutResponsibleAlsoTakeTheTreatmentsWithoutOne() {
        Map<String, Long> before = treatmentQuery.countByStatus(TreatmentFilter.ALL);
        TestTreatments.insert(jdbc, "ABANDONED", LocalDate.now().minusDays(40));
        assignedTo(insertPhysiotherapist(), "ABANDONED", 40);

        Map<String, Long> after = treatmentQuery.countByStatus(TreatmentFilter.ALL);

        assertThat(after.get("ABANDONED") - before.get("ABANDONED")).isEqualTo(2);
        assertThat(treatmentQuery.countByStatus(new TreatmentFilter("ABANDONED", null)).get("ABANDONED"))
                .isEqualTo(after.get("ABANDONED"));
    }

    @Test
    void theIdsWithAStatusAreThoseOfEveryTreatmentInThatStatusWhateverItsResponsible() {
        Long active = TestTreatments.insert(jdbc, "ACTIVE", LocalDate.now().minusDays(5));
        Long completed = assignedTo(insertPhysiotherapist(), "COMPLETED", 60);

        assertThat(treatmentQuery.idsWithStatus("ACTIVE")).contains(active).doesNotContain(completed);
        assertThat(treatmentQuery.idsWithStatus("COMPLETED")).contains(completed).doesNotContain(active);
        assertThat(treatmentQuery.idsWithStatus(null)).contains(active, completed);
    }

    @Test
    void theSearchListsTheTreatmentsOfTheFilterFromTheMostRecentStartDateWithTheirPatientAndResponsible() {
        Long physio = insertPhysiotherapist();
        Long oldest = assignedTo(physio, "COMPLETED", 300);
        Long newest = assignedTo(physio, "ACTIVE", 10);
        Long middle = assignedTo(physio, "ABANDONED", 100);
        Long sameDayAsMiddle = assignedTo(physio, "COMPLETED", 100);
        Long handedOver = assignedTo(insertPhysiotherapist(), "ACTIVE", 5);
        closedAssignment(handedOver, physio);

        Page<PatientTreatment> page = treatmentQuery.search(new TreatmentFilter(null, physio), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(PatientTreatment::id)
                .containsExactly(newest, sameDayAsMiddle, middle, oldest);
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent().getFirst()).satisfies(treatment -> {
            assertThat(treatment.patientId()).isEqualTo(jdbc.queryForObject("""
                    SELECT r.patient_id FROM treatments t JOIN clinical_records r ON r.id = t.clinical_record_id
                    WHERE t.id = ?""", Long.class, newest));
            assertThat(treatment.status()).isEqualTo("ACTIVE");
            assertThat(treatment.active()).isTrue();
            assertThat(treatment.startDate()).isEqualTo(LocalDate.now().minusDays(10));
            assertThat(treatment.diagnosisCode()).isEqualTo("M54.5");
            assertThat(treatment.diagnosisDescription()).isEqualTo("Lumbago no especificado");
            assertThat(treatment.responsibleId()).isEqualTo(physio);
        });

        Page<PatientTreatment> completed = treatmentQuery.search(new TreatmentFilter("COMPLETED", physio),
                PageRequest.of(1, 1));
        assertThat(completed.getContent()).extracting(PatientTreatment::id).containsExactly(oldest);
        assertThat(completed.getTotalElements()).isEqualTo(2);
        assertThat(completed.getTotalPages()).isEqualTo(2);
    }

    @Test
    void aTreatmentWithoutResponsibleIsListedWithoutOne() {
        Long withoutResponsible = TestTreatments.insert(jdbc, "ACTIVE", LocalDate.now().plusDays(3650));

        assertThat(treatmentQuery.search(new TreatmentFilter("ACTIVE", null), PageRequest.of(0, 1)).getContent())
                .singleElement()
                .satisfies(treatment -> {
                    assertThat(treatment.id()).isEqualTo(withoutResponsible);
                    assertThat(treatment.responsibleId()).isNull();
                });
    }

    @Test
    void aStatusThatIsNotATreatmentStatusIsRejected() {
        assertThatThrownBy(() -> treatmentQuery.countByStatus(new TreatmentFilter("PAUSED", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> treatmentQuery.idsWithStatus("PAUSED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----- the period of the treatment (HU-15) -----

    /**
     * October 2025, in Quito: a treatment is kept if it started on or before October 31 and, by October 1, it was still
     * active, had started or closed. It closes on the day, in Quito, of its last status change; its status is the
     * current one.
     */
    @Test
    void theRangeKeepsTheTreatmentsThatWereInProgressSomeDayOfIt() {
        Long physio = insertPhysiotherapist();
        Long startedBeforeStillActive = startedOn(physio, "ACTIVE", "2025-09-01");
        Long closedBeforeTheRange = startedOn(physio, "COMPLETED", "2025-08-01");
        closedAt(closedBeforeTheRange, physio, "COMPLETED", "2025-09-30T17:00:00Z");
        // 23:30 of September 30 in Quito is already October 1 in UTC: it closed the day before the range
        Long closedLateTheDayBefore = startedOn(physio, "ABANDONED", "2025-08-01");
        closedAt(closedLateTheDayBefore, physio, "ABANDONED", "2025-10-01T04:30:00Z");
        // 00:30 of October 1 in Quito: it was in progress the first day of the range
        Long closedEarlyTheFirstDay = startedOn(physio, "COMPLETED", "2025-08-01");
        closedAt(closedEarlyTheFirstDay, physio, "COMPLETED", "2025-10-01T05:30:00Z");
        Long startedTheLastDay = startedOn(physio, "ACTIVE", "2025-10-31");
        Long startedAfterTheRange = startedOn(physio, "ACTIVE", "2025-11-01");
        // Closed without a recorded change (inserted data): its start inside the range is enough
        Long startedInsideWithoutChanges = startedOn(physio, "COMPLETED", "2025-10-15");
        // Finished after the range: in progress during it, and counted with its current status
        Long closedAfterTheRange = startedOn(physio, "COMPLETED", "2025-09-01");
        closedAt(closedAfterTheRange, physio, "COMPLETED", "2025-11-10T15:00:00Z");
        LocalDate october1 = LocalDate.parse("2025-10-01");
        LocalDate october31 = LocalDate.parse("2025-10-31");
        TreatmentFilter october = new TreatmentFilter(null, physio, october1, october31);

        assertThat(treatmentQuery.countByStatus(october))
                .containsExactly(Map.entry("ACTIVE", 2L), Map.entry("COMPLETED", 3L), Map.entry("ABANDONED", 0L));
        assertThat(treatmentQuery.list(october)).extracting(PatientTreatment::id).containsExactly(startedTheLastDay,
                startedInsideWithoutChanges, closedAfterTheRange, startedBeforeStillActive, closedEarlyTheFirstDay);
        Page<PatientTreatment> firstPage = treatmentQuery.search(october, PageRequest.of(0, 2));
        assertThat(firstPage.getContent()).extracting(PatientTreatment::id)
                .containsExactly(startedTheLastDay, startedInsideWithoutChanges);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);

        // Either bound alone
        assertThat(treatmentQuery.list(new TreatmentFilter(null, physio, october1, null)))
                .extracting(PatientTreatment::id).containsExactly(startedAfterTheRange, startedTheLastDay,
                        startedInsideWithoutChanges, closedAfterTheRange, startedBeforeStillActive,
                        closedEarlyTheFirstDay);
        assertThat(treatmentQuery.list(new TreatmentFilter(null, physio, null, october31)))
                .extracting(PatientTreatment::id).containsExactly(startedTheLastDay, startedInsideWithoutChanges,
                        closedAfterTheRange, startedBeforeStillActive, closedEarlyTheFirstDay,
                        closedLateTheDayBefore, closedBeforeTheRange);
        // With the status
        assertThat(treatmentQuery.list(new TreatmentFilter("COMPLETED", physio, october1, october31)))
                .extracting(PatientTreatment::id)
                .containsExactly(startedInsideWithoutChanges, closedAfterTheRange, closedEarlyTheFirstDay);
    }

    @Test
    void theListWithoutRangeHasEveryTreatmentOfTheFilterInTheOrderOfTheSearch() {
        Long physio = insertPhysiotherapist();
        Long oldest = assignedTo(physio, "COMPLETED", 300);
        Long newest = assignedTo(physio, "ACTIVE", 10);
        Long middle = assignedTo(physio, "ABANDONED", 100);

        List<PatientTreatment> list = treatmentQuery.list(new TreatmentFilter(null, physio));

        assertThat(list).extracting(PatientTreatment::id).containsExactly(newest, middle, oldest);
        assertThat(list).isEqualTo(treatmentQuery.search(new TreatmentFilter(null, physio), PageRequest.of(0, 50))
                .getContent());
        assertThat(treatmentQuery.list(new TreatmentFilter(null, 999_999_999L))).isEmpty();
        assertThatThrownBy(() -> treatmentQuery.list(new TreatmentFilter("PAUSED", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Long startedOn(Long physiotherapistId, String status, String startDate) {
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

    /** A treatment of a new patient that started the given days ago, with the physiotherapist as responsible. */
    private Long assignedTo(Long physiotherapistId, String status, int daysAgo) {
        Long treatmentId = TestTreatments.insert(jdbc, status, LocalDate.now().minusDays(daysAgo));
        TestTreatments.assignResponsible(jdbc, treatmentId, physiotherapistId);
        return treatmentId;
    }

    /** A past assignment: history, not the current responsible. */
    private void closedAssignment(Long treatmentId, Long physiotherapistId) {
        jdbc.update("""
                INSERT INTO responsible_assignments (treatment_id, physiotherapist_id, valid_from, valid_to)
                VALUES (?, ?, now() - INTERVAL '2 days', now() - INTERVAL '1 day')""", treatmentId,
                physiotherapistId);
    }

    private Long insertPhysiotherapist() {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, 'not-a-real-hash', 'Fisioterapeuta de prueba', 'PHYSIOTHERAPIST')
                RETURNING id""", Long.class, "puerto-" + UUID.randomUUID() + "@rehabelite.test");
    }

}
