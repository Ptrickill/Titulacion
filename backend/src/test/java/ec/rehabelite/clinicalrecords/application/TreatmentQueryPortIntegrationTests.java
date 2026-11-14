package ec.rehabelite.clinicalrecords.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.TestPatients;
import ec.rehabelite.TestTreatments;
import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** HU-12: what the public port of clinicalrecords tells other modules about the treatments of a patient. */
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

    private Long insertPhysiotherapist() {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, 'not-a-real-hash', 'Fisioterapeuta de prueba', 'PHYSIOTHERAPIST')
                RETURNING id""", Long.class, "puerto-" + UUID.randomUUID() + "@rehabelite.test");
    }

}
