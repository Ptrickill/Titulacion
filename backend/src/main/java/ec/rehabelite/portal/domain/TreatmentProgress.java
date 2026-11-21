package ec.rehabelite.portal.domain;

import ec.rehabelite.clinicalrecords.TreatmentSummary;
import ec.rehabelite.sessions.SessionSummary;
import java.util.ArrayList;
import java.util.List;

/**
 * The progress of a treatment as its patient sees it in the portal (HU-12): sessions that took place against the
 * planned ones, and the evolution of the pain since the initial evaluation.
 *
 * @param completedSessions sessions that took place (COMPLETED); rescheduled sessions and no-shows do not count
 * @param plannedSessions   sessions of the therapeutic plan
 * @param percentage        completed over planned, rounded down and never above 100
 * @param initialPainScore  EVA of the initial evaluation
 * @param lastPainScore     EVA of the last session that took place; null when there is none yet
 * @param painReduction     initial EVA minus the last one, in points: positive when the pain went down, negative
 *                          when it went up; null when no session has taken place
 * @param painCurve         the initial evaluation followed by the sessions that took place, in chronological order
 */
public record TreatmentProgress(Long treatmentId, int completedSessions, int plannedSessions, int percentage,
        int initialPainScore, Integer lastPainScore, Integer painReduction, List<PainEvolutionPoint> painCurve) {

    public static final String INITIAL_EVALUATION = "Evaluación inicial";
    static final int FULL_PLAN = 100;

    /**
     * The progress from the starting point of the treatment and its sessions in chronological order. Same rules as
     * the pain curve of the therapy timeline (HU-11): it always starts with the initial evaluation, and only the
     * sessions that took place are points of it.
     */
    public static TreatmentProgress of(TreatmentSummary treatment, List<SessionSummary> sessions) {
        List<PainEvolutionPoint> curve = new ArrayList<>();
        curve.add(new PainEvolutionPoint(INITIAL_EVALUATION, treatment.startDate(), treatment.initialPainScore(),
                null));
        sessions.stream()
                .filter(SessionSummary::completed)
                .map(session -> new PainEvolutionPoint("Sesión " + session.sequenceNumber(), session.sessionDate(),
                        session.painScore(), session.sequenceNumber()))
                .forEach(curve::add);

        int completed = curve.size() - 1;
        Integer lastPainScore = completed == 0 ? null : curve.getLast().painScore();
        Integer painReduction = lastPainScore == null ? null : treatment.initialPainScore() - lastPainScore;
        return new TreatmentProgress(treatment.id(), completed, treatment.plannedSessions(),
                percentageOf(completed, treatment.plannedSessions()), treatment.initialPainScore(), lastPainScore,
                painReduction, List.copyOf(curve));
    }

    /**
     * Completed over planned, rounded down so that 100 only appears once the whole plan is done; more sessions than
     * planned stay at 100. A plan always has at least one session (CHECK constraint); 0 is only a safeguard.
     */
    static int percentageOf(int completed, int planned) {
        if (planned <= 0) {
            return 0;
        }
        return (int) Math.min(FULL_PLAN, (long) completed * FULL_PLAN / planned);
    }

}
