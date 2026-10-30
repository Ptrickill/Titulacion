package ec.rehabelite.clinicalrecords.domain;

import jakarta.persistence.LockModeType;
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

    /** Locks the treatment row until the transaction ends, so concurrent changes run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Treatment t WHERE t.id = :id")
    Optional<Treatment> findForUpdate(Long id);

}
