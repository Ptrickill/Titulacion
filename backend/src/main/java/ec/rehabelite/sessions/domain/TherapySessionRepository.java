package ec.rehabelite.sessions.domain;

import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TherapySessionRepository extends JpaRepository<TherapySession, Long>,
        JpaSpecificationExecutor<TherapySession> {

    /**
     * Highest sequence number of the treatment, 0 without sessions. Only meaningful while the treatment row is
     * locked: otherwise two registrations could read the same value.
     */
    @Query("SELECT COALESCE(MAX(s.sequenceNumber), 0) FROM TherapySession s WHERE s.treatmentId = :treatmentId")
    int maxSequenceNumber(Long treatmentId);

    Page<TherapySession> findByTreatmentId(Long treatmentId, Pageable pageable);

    /**
     * The sessions of the treatment in chronological order (date, time and sequence number), optionally between two
     * days, both included; a null bound does not filter. One statement, whatever the number of sessions (HU-11).
     */
    default List<TherapySession> findTimeline(Long treatmentId, LocalDate from, LocalDate to) {
        // Only the bounds that were sent become predicates: a null parameter has no SQL type in PostgreSQL
        Specification<TherapySession> timeline = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("treatmentId"), treatmentId));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sessionDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sessionDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return findAll(timeline, Sort.by("sessionDate", "sessionTime", "sequenceNumber"));
    }

    /** Sessions of the treatment with the given status, e.g. the completed ones for the progress of the plan. */
    long countByTreatmentIdAndStatus(Long treatmentId, SessionStatus status);

    /** Locks the session row until the transaction ends, so two corrections never overwrite each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TherapySession s WHERE s.id = :id")
    Optional<TherapySession> findForUpdate(Long id);

}
