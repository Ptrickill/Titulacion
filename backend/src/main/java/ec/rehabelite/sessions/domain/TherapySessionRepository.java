package ec.rehabelite.sessions.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TherapySessionRepository extends JpaRepository<TherapySession, Long> {

    /**
     * Highest sequence number of the treatment, 0 without sessions. Only meaningful while the treatment row is
     * locked: otherwise two registrations could read the same value.
     */
    @Query("SELECT COALESCE(MAX(s.sequenceNumber), 0) FROM TherapySession s WHERE s.treatmentId = :treatmentId")
    int maxSequenceNumber(Long treatmentId);

    Page<TherapySession> findByTreatmentId(Long treatmentId, Pageable pageable);

    /** Locks the session row until the transaction ends, so two corrections never overwrite each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TherapySession s WHERE s.id = :id")
    Optional<TherapySession> findForUpdate(Long id);

}
