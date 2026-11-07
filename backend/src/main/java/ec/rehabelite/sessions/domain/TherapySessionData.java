package ec.rehabelite.sessions.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The fields of a therapy session the physiotherapist writes and may later correct. The sequence number and the
 * responsible are not here: the server assigns them and they never change.
 *
 * @param patientInstructions optional; blank is stored as null
 */
public record TherapySessionData(LocalDate sessionDate, LocalTime sessionTime, String sessionType,
        String activitiesPerformed, String clinicalObservations, String patientInstructions, int painScore,
        ProgressStatus progressStatus, SessionStatus status) {

    /** Same data with the optional text normalized, so "" and null compare as equal. */
    TherapySessionData normalized() {
        String instructions = patientInstructions == null || patientInstructions.isBlank() ? null : patientInstructions;
        return new TherapySessionData(sessionDate, sessionTime, sessionType, activitiesPerformed,
                clinicalObservations, instructions, painScore, progressStatus, status);
    }

    /** Never print clinical data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "TherapySessionData[...]";
    }

}
