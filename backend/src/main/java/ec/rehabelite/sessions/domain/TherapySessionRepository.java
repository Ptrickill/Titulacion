package ec.rehabelite.sessions.domain;

import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
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
import org.springframework.data.repository.query.Param;

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

    /**
     * Sessions with the given status in each of the treatments, in one statement (HU-12). A treatment without such
     * sessions has no row.
     */
    @Query("""
            SELECT s.treatmentId AS treatmentId, COUNT(s) AS sessions FROM TherapySession s
            WHERE s.treatmentId IN :treatmentIds AND s.status = :status
            GROUP BY s.treatmentId""")
    List<TreatmentSessionCount> countByTreatmentIdsAndStatus(Collection<Long> treatmentIds, SessionStatus status);

    /**
     * Sessions of the treatments per physiotherapist who registered them and per status, in one statement (HU-14);
     * only the physiotherapist's if {@code responsibleId} is not null. The ids go as one array parameter, so a long
     * list never reaches the limit of bind parameters of PostgreSQL. With {@code from} or {@code to} (HU-15), only the
     * sessions dated between them, both included: session_date is already the date in Quito.
     */
    @Query(nativeQuery = true, value = """
            SELECT s.responsible_id AS "responsibleId", s.status AS "status", count(*) AS "sessions"
            FROM therapy_sessions s
            WHERE s.treatment_id = ANY (CAST(:treatmentIds AS bigint[]))
              AND (CAST(:responsibleId AS bigint) IS NULL OR s.responsible_id = CAST(:responsibleId AS bigint))
              AND (CAST(:from AS date) IS NULL OR s.session_date >= CAST(:from AS date))
              AND (CAST(:to AS date) IS NULL OR s.session_date <= CAST(:to AS date))
            GROUP BY s.responsible_id, s.status""")
    List<ResponsibleSessionCount> countByResponsibleAndStatus(@Param("treatmentIds") Long[] treatmentIds,
            @Param("responsibleId") Long responsibleId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * The most recent session of the treatment (date, time and sequence number) with instructions for the patient,
     * whatever its status (HU-13). Blank instructions are stored as null, so IS NOT NULL is enough.
     */
    @Query("""
            SELECT s FROM TherapySession s
            WHERE s.treatmentId = :treatmentId AND s.patientInstructions IS NOT NULL
            ORDER BY s.sessionDate DESC, s.sessionTime DESC, s.sequenceNumber DESC
            LIMIT 1""")
    Optional<TherapySession> findLatestWithPatientInstructions(Long treatmentId);

    /** Locks the session row until the transaction ends, so two corrections never overwrite each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TherapySession s WHERE s.id = :id")
    Optional<TherapySession> findForUpdate(Long id);

}
