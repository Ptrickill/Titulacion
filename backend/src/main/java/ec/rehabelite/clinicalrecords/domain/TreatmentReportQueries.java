package ec.rehabelite.clinicalrecords.domain;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Queries of the operations reports (HU-14), a fragment of {@link TreatmentRepository}. Every criterion is optional
 * (null does not filter); the responsible is the current one (responsible_assignments.valid_to is null).
 */
public interface TreatmentReportQueries {

    /** Treatments per status, in one statement; a status without treatments has no entry. */
    Map<TreatmentStatus, Long> countByStatus(TreatmentStatus status, Long responsibleId);

    /** Ids of the treatments with the status (every treatment if null), in one statement. */
    List<Long> findIdsByStatus(TreatmentStatus status);

    /**
     * One page of treatments with their patient, diagnosis and current responsible, from the most recent start date
     * to the oldest. Always two statements, the page and the total, so the number of statements never depends on the
     * results.
     */
    Page<TreatmentListRow> findReportPage(TreatmentStatus status, Long responsibleId, Pageable pageable);

}
