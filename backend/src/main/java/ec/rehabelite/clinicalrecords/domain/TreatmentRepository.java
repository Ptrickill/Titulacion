package ec.rehabelite.clinicalrecords.domain;

import ec.rehabelite.clinicalrecords.ActiveTreatment;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TreatmentRepository extends JpaRepository<Treatment, Long>, TreatmentReportQueries {

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

    /** ACTIVE treatments per current responsible (MEJ-04), in one statement. */
    @Query("""
            SELECT new ec.rehabelite.clinicalrecords.domain.ResponsibleLoad(a.physiotherapistId, COUNT(t))
            FROM Treatment t JOIN ResponsibleAssignment a ON a.treatment = t AND a.validTo IS NULL
            WHERE t.status = :status
            GROUP BY a.physiotherapistId""")
    List<ResponsibleLoad> countByCurrentResponsible(TreatmentStatus status);

    /**
     * Treatments with the status and their current responsible, if any (MEJ-04), the most recent start first. At
     * most one current assignment per treatment (uq_responsible_assignments_current), so no row repeats.
     */
    @Query(value = """
            SELECT new ec.rehabelite.clinicalrecords.ActiveTreatment(t.id, r.patientId, t.startDate,
                   a.physiotherapistId)
            FROM Treatment t JOIN t.clinicalRecord r
            LEFT JOIN ResponsibleAssignment a ON a.treatment = t AND a.validTo IS NULL
            WHERE t.status = :status
            ORDER BY t.startDate DESC, t.id DESC""",
            countQuery = "SELECT COUNT(t) FROM Treatment t WHERE t.status = :status")
    Page<ActiveTreatment> findWithCurrentResponsible(TreatmentStatus status, Pageable pageable);

    /** As {@link #findWithCurrentResponsible}, only the treatments whose current responsible is one of the ids. */
    @Query(value = """
            SELECT new ec.rehabelite.clinicalrecords.ActiveTreatment(t.id, r.patientId, t.startDate,
                   a.physiotherapistId)
            FROM Treatment t JOIN t.clinicalRecord r
            JOIN ResponsibleAssignment a ON a.treatment = t AND a.validTo IS NULL
            WHERE t.status = :status AND a.physiotherapistId IN :responsibleIds
            ORDER BY t.startDate DESC, t.id DESC""",
            countQuery = """
                    SELECT COUNT(t) FROM Treatment t
                    JOIN ResponsibleAssignment a ON a.treatment = t AND a.validTo IS NULL
                    WHERE t.status = :status AND a.physiotherapistId IN :responsibleIds""")
    Page<ActiveTreatment> findWithCurrentResponsibleIn(TreatmentStatus status, Collection<Long> responsibleIds,
            Pageable pageable);

    /** Locks the treatment row until the transaction ends, so concurrent changes run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Treatment t WHERE t.id = :id")
    Optional<Treatment> findForUpdate(Long id);

}
