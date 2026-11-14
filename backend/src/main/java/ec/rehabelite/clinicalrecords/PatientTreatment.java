package ec.rehabelite.clinicalrecords;

import java.time.LocalDate;

/**
 * A treatment as the patient portal lists it (HU-12): its plan, its status and who is in charge. Nothing else of its
 * clinical content (complaint, illness, goal...), and never the entity itself.
 *
 * @param status               value of treatments.status: ACTIVE, COMPLETED or ABANDONED
 * @param active               whether the status is ACTIVE
 * @param startDate            first day of the treatment, the day of the initial evaluation
 * @param initialPainScore     EVA of the initial evaluation, 0 to 10
 * @param plannedSessions      number of sessions of the therapeutic plan
 * @param diagnosisCode        ICD-10 code of the diagnosis
 * @param diagnosisDescription Spanish description of the diagnosis in the catalog
 * @param responsibleId        user id of the current responsible physiotherapist; null if the treatment has none
 */
public record PatientTreatment(Long id, Long patientId, String status, boolean active, LocalDate startDate,
        int initialPainScore, int plannedSessions, String diagnosisCode, String diagnosisDescription,
        Long responsibleId) {
}
