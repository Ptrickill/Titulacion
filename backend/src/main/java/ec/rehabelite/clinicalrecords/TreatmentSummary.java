package ec.rehabelite.clinicalrecords;

import java.time.LocalDate;

/**
 * What other modules (sessions, ...) may know about a treatment: its identity, owner, whether it is still active, and
 * its starting point (HU-11): the initial pain score, the planned sessions and the ICD-10 diagnosis. Nothing else of
 * its clinical content (complaint, illness, goal...), and never the entity itself.
 *
 * @param active               whether the status is ACTIVE (sessions are only registered on active treatments)
 * @param startDate            first day of the treatment (no session can be dated before it)
 * @param initialPainScore     EVA of the initial evaluation, 0 to 10
 * @param plannedSessions      number of sessions of the therapeutic plan
 * @param diagnosisCode        ICD-10 code of the diagnosis
 * @param diagnosisDescription Spanish description of the diagnosis in the catalog
 */
public record TreatmentSummary(Long id, Long patientId, boolean active, LocalDate startDate, int initialPainScore,
        int plannedSessions, String diagnosisCode, String diagnosisDescription) {
}
