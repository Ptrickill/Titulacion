package ec.rehabelite;

import java.time.LocalDate;

/** Request bodies of therapy sessions (HU-10) for integration tests; dates are relative to today. */
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

}
