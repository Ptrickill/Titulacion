package ec.rehabelite;

import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Request bodies of therapy sessions (HU-10) for integration tests, and sessions inserted with SQL; dates are relative
 * to today.
 */
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
     * A session with the given date, time ({@code HH:mm}), pain score and status, e.g. for the timeline of HU-11.
     */
    public static String body(LocalDate date, String time, int painScore, String status) {
        return body(date, painScore, "Movilización lumbar y TENS 15 minutos", "")
                .replace("\"sessionTime\":\"09:30\"", "\"sessionTime\":\"" + time + "\"")
                .replace("\"status\":\"COMPLETED\"", "\"status\":\"" + status + "\"");
    }

    /**
     * Inserts a session with SQL, without audit entries nor versions, for tests that only read sessions; {@code time}
     * is {@code HH:mm}. Returns its id.
     */
    public static Long insert(JdbcTemplate jdbc, Long treatmentId, Long responsibleId, int sequenceNumber,
            LocalDate date, String time, int painScore, String status) {
        return jdbc.queryForObject("""
                INSERT INTO therapy_sessions (treatment_id, responsible_id, sequence_number, session_date, session_time,
                                              session_type, activities_performed, clinical_observations,
                                              patient_instructions, pain_score, progress_status, status)
                VALUES (?, ?, ?, ?, ?, 'Fisioterapia lumbar mecánica', 'Movilización lumbar',
                        'Observación clínica interna', 'Calor local 15 minutos', ?, 'IMPROVING', ?)
                RETURNING id""", Long.class, treatmentId, responsibleId, sequenceNumber, date, LocalTime.parse(time),
                painScore, status);
    }

}
