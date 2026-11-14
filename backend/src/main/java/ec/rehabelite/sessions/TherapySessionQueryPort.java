package ec.rehabelite.sessions;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public API of the sessions module: how other modules (the patient portal, ...) read therapy sessions. They never
 * use the module's web, application or domain packages. Reads through this port are not audited here: the caller
 * audits its own operation.
 */
public interface TherapySessionQueryPort {

    /**
     * The sessions of the treatment in chronological order (date, time and sequence number), whatever their status.
     * One statement, whatever the number of sessions.
     */
    List<SessionSummary> chronological(Long treatmentId);

    /**
     * The number of sessions that took place (COMPLETED) in each of the treatments, in one statement. Every id of
     * the collection is a key of the result; a treatment without completed sessions maps to 0.
     */
    Map<Long, Long> completedCounts(Collection<Long> treatmentIds);

    /**
     * The sessions of the treatment as its patient may read them (HU-13), in chronological order (date, time and
     * sequence number) and whatever their status. One statement, whatever the number of sessions.
     */
    List<PatientSessionView> patientView(Long treatmentId);

    /**
     * The most recent session (by date, time and sequence number) that has instructions for the patient, whatever
     * its status; empty when no session of the treatment has any. One statement that reads a single row.
     */
    Optional<PatientSessionView> latestPatientInstruction(Long treatmentId);

}
