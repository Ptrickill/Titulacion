package ec.rehabelite.clinicalrecords.domain;

import java.time.LocalDate;

/**
 * A treatment as the operations reports list it (HU-14): read with a JPQL constructor expression, so the clinical
 * content of the treatment (complaint, illness, goal...) is never loaded.
 *
 * @param responsibleId user id of the current responsible physiotherapist; null if the treatment has none
 */
public record TreatmentListRow(Long id, Long patientId, TreatmentStatus status, LocalDate startDate,
        short initialPainScore, int plannedSessions, String diagnosisCode, String diagnosisDescription,
        Long responsibleId) {
}
