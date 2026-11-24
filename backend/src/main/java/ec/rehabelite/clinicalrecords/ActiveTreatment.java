package ec.rehabelite.clinicalrecords;

import java.time.LocalDate;

/**
 * An ACTIVE treatment as the operations director's assignment views see it (MEJ-04): its patient, start date and
 * current responsible (null if it has none). No clinical content at all, not even the diagnosis (LOPDP, the minimum
 * needed to assign a physiotherapist).
 */
public record ActiveTreatment(Long id, Long patientId, LocalDate startDate, Long responsibleId) {
}
