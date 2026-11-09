package ec.rehabelite.sessions.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A point of the pain curve of a treatment (HU-11).
 *
 * @param label          Spanish label shown on the chart: "Evaluación inicial" or "Sesión N"
 * @param date           day of the initial evaluation (start of the treatment) or of the session
 * @param painScore      EVA, 0 to 10
 * @param sessionId      the session of the point; null for the initial evaluation
 * @param sequenceNumber sequence number of the session; null for the initial evaluation
 */
public record PainPoint(String label, LocalDate date, int painScore, Long sessionId, Integer sequenceNumber) {

    public static final String INITIAL_EVALUATION = "Evaluación inicial";

    /**
     * The pain curve: always starts with the initial evaluation of the treatment, followed by the COMPLETED sessions
     * in the given (chronological) order. RESCHEDULED and NO_SHOW sessions are not points: no therapy took place.
     */
    public static List<PainPoint> curveOf(LocalDate startDate, int initialPainScore, List<TherapySession> sessions) {
        List<PainPoint> curve = new ArrayList<>();
        curve.add(new PainPoint(INITIAL_EVALUATION, startDate, initialPainScore, null, null));
        sessions.stream()
                .filter(session -> session.getStatus() == SessionStatus.COMPLETED)
                .map(session -> new PainPoint("Sesión " + session.getSequenceNumber(), session.getSessionDate(),
                        session.getPainScore(), session.getId(), session.getSequenceNumber()))
                .forEach(curve::add);
        return List.copyOf(curve);
    }

}
