package ec.rehabelite.sessions;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What other modules (the patient portal, ...) may know about a therapy session: when it was, whether it took place
 * and the pain the patient reported. It never carries the clinical observations, which are an internal note of the
 * physiotherapy team, so a module that reads sessions through this type cannot show them by mistake.
 *
 * @param sequenceNumber sequence number of the session in its treatment
 * @param status         value of therapy_sessions.status: COMPLETED, RESCHEDULED or NO_SHOW
 * @param completed      whether the session took place (status COMPLETED); only these count for the progress of the
 *                       plan and for the pain curve
 * @param painScore      EVA, 0 to 10
 */
public record SessionSummary(Long id, Long treatmentId, int sequenceNumber, LocalDate sessionDate,
        LocalTime sessionTime, String status, boolean completed, int painScore) {
}
