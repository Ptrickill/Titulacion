package ec.rehabelite.clinicalrecords;

import java.time.LocalDate;

/**
 * What other modules (sessions, ...) may know about a treatment: no clinical content, only its identity, owner and
 * whether it is still active.
 *
 * @param active    whether the status is ACTIVE (sessions are only registered on active treatments)
 * @param startDate first day of the treatment (no session can be dated before it)
 */
public record TreatmentSummary(Long id, Long patientId, boolean active, LocalDate startDate) {
}
