package ec.rehabelite.clinicalrecords.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

    /**
     * The treatment with its clinical record, diagnosis and responsible assignments, ready to be mapped outside a
     * transaction. A fetch graph makes every attribute it does not list lazy, so the diagnosis must be listed too.
     */
    @EntityGraph(attributePaths = {"clinicalRecord", "diagnosis", "responsibleAssignments"})
    Optional<Treatment> findWithDetailsById(Long id);

    /**
     * The treatment with its clinical record (for the patient id) and diagnosis in one statement: what
     * TreatmentQueryPort tells the other modules.
     */
    @EntityGraph(attributePaths = {"clinicalRecord", "diagnosis"})
    Optional<Treatment> findSummaryById(Long id);

    /** The treatments of the patient with their diagnosis, in one statement; in no particular order. */
    @Query("""
            SELECT t FROM Treatment t
            JOIN FETCH t.diagnosis
            JOIN FETCH t.clinicalRecord r
            WHERE r.patientId = :patientId""")
    List<Treatment> findByPatientId(Long patientId);

    /** Locks the treatment row until the transaction ends, so concurrent changes run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Treatment t WHERE t.id = :id")
    Optional<Treatment> findForUpdate(Long id);

}
