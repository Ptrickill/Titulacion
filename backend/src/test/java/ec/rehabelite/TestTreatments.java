package ec.rehabelite;

import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Synthetic treatments for integration tests that are not about clinical records (e.g. therapy sessions): a new
 * patient with a clinical record and one treatment, inserted with SQL, without audit entries, versions nor
 * responsible assignments.
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
        Long recordId = jdbc.queryForObject("""
                INSERT INTO clinical_records (patient_id, habits, physical_activity, allergies, current_medication,
                                              personal_history, surgical_history, family_history)
                VALUES (?, 'No fuma', 'Camina', 'Ninguna', 'Ninguna', 'Ninguno', 'Ninguno', 'Ninguno')
                RETURNING id""", Long.class, patientId);
        return jdbc.queryForObject("""
                INSERT INTO treatments (clinical_record_id, diagnosis_code, chief_complaint, current_illness,
                                        referring_physician, complementary_tests, severity, pain_type, pain_location,
                                        initial_pain_score, pain_frequency, session_frequency, goal, planned_sessions,
                                        status, start_date)
                VALUES (?, 'M54.5', 'Dolor lumbar', 'Tres semanas', 'Dr. Andrade', 'Radiografía', 'MODERATE',
                        'Punzante', 'Lumbar', 7, 'Intermitente', '3 por semana', 'Reducir el dolor', 12, ?, ?)
                RETURNING id""", Long.class, recordId, status, startDate);
    }

}
