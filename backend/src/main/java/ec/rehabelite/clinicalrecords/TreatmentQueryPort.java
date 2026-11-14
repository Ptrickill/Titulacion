package ec.rehabelite.clinicalrecords;

import java.util.List;
import java.util.Optional;

/**
 * Public API of the clinicalrecords module: how other modules (sessions, ...) look a treatment up. They never use
 * the module's web, application or domain packages.
 */
public interface TreatmentQueryPort {

    /** The treatment, without locking it; empty when it does not exist. */
    Optional<TreatmentSummary> find(Long treatmentId);

    /**
     * The treatment, locking its row (PESSIMISTIC_WRITE) until the caller's transaction ends, so concurrent session
     * registrations on the same treatment run one after the other (HU-10: consecutive sequence numbers). Must be
     * called inside a transaction.
     */
    Optional<TreatmentSummary> lockForSessionRegistration(Long treatmentId);

    /**
     * Every treatment of the patient with its current responsible (HU-12): the active one first, then the others
     * from the most recent to the oldest. Empty when the patient has no clinical record. Two statements, whatever
     * the number of treatments.
     */
    List<PatientTreatment> findByPatient(Long patientId);

}
