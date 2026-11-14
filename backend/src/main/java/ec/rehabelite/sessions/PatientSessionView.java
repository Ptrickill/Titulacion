package ec.rehabelite.sessions;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A therapy session as its patient may read it (HU-13): when it was, who attended, the pain reported, what was done
 * and the instructions the physiotherapist wrote for the patient. It never carries the clinical observations nor the
 * progress status, which are internal notes of the physiotherapy team.
 *
 * @param sequenceNumber      sequence number of the session in its treatment
 * @param sessionType         kind of session, as the physiotherapist wrote it
 * @param responsibleId       user id of the physiotherapist who registered the session
 * @param painScore           EVA, 0 to 10
 * @param activitiesPerformed what was done in the session
 * @param status              value of therapy_sessions.status: COMPLETED, RESCHEDULED or NO_SHOW
 * @param patientInstructions instructions for the patient; null when the session has none
 */
public record PatientSessionView(int sequenceNumber, LocalDate sessionDate, LocalTime sessionTime,
        String sessionType, Long responsibleId, int painScore, String activitiesPerformed, String status,
        String patientInstructions) {

    /** Never print clinical data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "PatientSessionView[sequenceNumber=" + sequenceNumber + "]";
    }

}
