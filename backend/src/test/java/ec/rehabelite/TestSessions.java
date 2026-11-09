package ec.rehabelite;

import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.jdbc.core.JdbcTemplate;

/** Therapy sessions (HU-10) for integration tests; dates are relative to today. */
public final class TestSessions {

    private TestSessions() {
    }

    /** A valid session of yesterday with the given pain score. */
    public static String body(int painScore) {
        return body(LocalDate.now().minusDays(1), painScore, "Movilización lumbar y TENS 15 minutos", "");
    }

    /**
     * A session with the given date, pain score and activities; {@code extraFields} is inserted as is at the start
     * of the object (e.g. {@code "\"responsibleId\":5,"}).
     */
    public static String body(LocalDate date, int painScore, String activities, String extraFields) {
        return """
                {%s"sessionDate":"%s","sessionTime":"09:30","sessionType":"Fisioterapia lumbar mecánica",
                 "activitiesPerformed":"%s","clinicalObservations":"Buena tolerancia al ejercicio",
                 "patientInstructions":"Calor local 15 minutos en casa","painScore":%d,
                 "progressStatus":"IMPROVING","status":"COMPLETED"}"""
                .formatted(extraFields, date, activities, painScore);
    }

    /**
     * Inserts a session with SQL, without audit entries nor versions, for tests that read sessions (e.g. the
     * timeline of HU-11) and need full control of their date, time, status and responsible; returns its id.
     */
    public static Long insert(JdbcTemplate jdbc, Long treatmentId, int sequenceNumber, Long responsibleId,
            LocalDate date, LocalTime time, int painScore, String status) {
        return jdbc.queryForObject("""
                INSERT INTO therapy_sessions (treatment_id, responsible_id, sequence_number, session_date, session_time,
                                              session_type, activities_performed, clinical_observations,
                                              patient_instructions, pain_score, progress_status, status, created_by)
                VALUES (?, ?, ?, ?, ?, 'Fisioterapia lumbar mecánica', 'Movilización lumbar y TENS 15 minutos',
                        'Buena tolerancia al ejercicio', 'Calor local 15 minutos en casa', ?, 'IMPROVING', ?, ?)
                RETURNING id""", Long.class, treatmentId, responsibleId, sequenceNumber, date, time, painScore, status,
                responsibleId);
    }

}
