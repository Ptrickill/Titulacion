package ec.rehabelite;

import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Synthetic treatments for integration tests that are not about clinical records (e.g. therapy sessions): a new
 * patient with a clinical record and one treatment, inserted with SQL, without audit entries, versions nor
 * responsible assignments. The pieces are also available one by one, for a patient with several treatments.
 */
public final class TestTreatments {

    private TestTreatments() {
    }

    /** An ACTIVE treatment that started 30 days ago; returns its id. */
    public static Long insertActive(JdbcTemplate jdbc) {
        return insert(jdbc, "ACTIVE", LocalDate.now().minusDays(30));
    }

    /** A treatment with the given status and start date, for a new patient; returns its id. */
    public static Long insert(JdbcTemplate jdbc, String status, LocalDate startDate) {
        Long patientId = TestPatients.insert(jdbc, "Paciente", "De Sesiones");
        return insertFor(jdbc, insertRecord(jdbc, patientId), status, startDate, 7, 12);
    }

    /** The clinical record of an existing patient, without treatments; returns its id. */
    public static Long insertRecord(JdbcTemplate jdbc, Long patientId) {
        return jdbc.queryForObject("""
                INSERT INTO clinical_records (patient_id, habits, physical_activity, allergies, current_medication,
                                              personal_history, surgical_history, family_history)
                VALUES (?, 'No fuma', 'Camina', 'Ninguna', 'Ninguna', 'Ninguno', 'Ninguno', 'Ninguno')
                RETURNING id""", Long.class, patientId);
    }

    /** A treatment (diagnosis M54.5) of an existing clinical record; returns its id. */
    public static Long insertFor(JdbcTemplate jdbc, Long recordId, String status, LocalDate startDate,
            int initialPainScore, int plannedSessions) {
        return jdbc.queryForObject("""
                INSERT INTO treatments (clinical_record_id, diagnosis_code, chief_complaint, current_illness,
                                        referring_physician, complementary_tests, severity, pain_type, pain_location,
                                        initial_pain_score, pain_frequency, session_frequency, goal, planned_sessions,
                                        status, start_date)
                VALUES (?, 'M54.5', 'Dolor lumbar', 'Tres semanas', 'Dr. Andrade', 'Radiografía', 'MODERATE',
                        'Punzante', 'Lumbar', ?, 'Intermitente', '3 por semana', 'Reducir el dolor', ?, ?, ?)
                RETURNING id""", Long.class, recordId, initialPainScore, plannedSessions, status, startDate);
    }

    /** Makes the physiotherapist the current responsible of a treatment that has none. */
    public static void assignResponsible(JdbcTemplate jdbc, Long treatmentId, Long physiotherapistId) {
        jdbc.update("INSERT INTO responsible_assignments (treatment_id, physiotherapist_id) VALUES (?, ?)",
                treatmentId, physiotherapistId);
    }

}
