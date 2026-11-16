package ec.rehabelite.clinicalrecords;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    /**
     * Treatments per status among those of the filter (HU-14): ACTIVE, COMPLETED and ABANDONED are always keys, with
     * 0 when there are none. One statement.
     *
     * @throws IllegalArgumentException if the status of the filter is not a treatment status
     */
    Map<String, Long> countByStatus(TreatmentFilter filter);

    /**
     * Ids of the treatments with the status, or of every treatment if it is null (HU-14: the sessions of the
     * treatments in that status). One statement.
     *
     * @throws IllegalArgumentException if the status is not a treatment status
     */
    List<Long> idsWithStatus(String status);

    /**
     * One page of the treatments of the filter (HU-14), from the most recent start date to the oldest, with their
     * patient id, diagnosis and current responsible. The pageable's sort is ignored. Always two statements: the page
     * and the total.
     *
     * @throws IllegalArgumentException if the status of the filter is not a treatment status
     */
    Page<PatientTreatment> search(TreatmentFilter filter, Pageable pageable);

}
