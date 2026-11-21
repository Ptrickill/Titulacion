package ec.rehabelite.clinicalrecords.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Queries of the operations reports (HU-14, HU-15), a fragment of {@link TreatmentRepository}. Every criterion is
 * optional (null does not filter); the responsible is the current one (responsible_assignments.valid_to is null).
 */
public interface TreatmentReportQueries {

    /**
     * Criteria of the reports.
     *
     * @param status        current status of the treatment
     * @param responsibleId current responsible
     * @param from          first day of the period, in Quito: the treatment had not closed before this day
     * @param to            last day of the period: the treatment started on or before this day
     */
    record ReportCriteria(TreatmentStatus status, Long responsibleId, LocalDate from, LocalDate to) {
    }

    /** Treatments per status, in one statement; a status without treatments has no entry. */
    Map<TreatmentStatus, Long> countByStatus(ReportCriteria criteria);

    /** Ids of the treatments with the status (every treatment if null), in one statement. */
    List<Long> findIdsByStatus(TreatmentStatus status);

    /**
     * One page of treatments with their patient, diagnosis and current responsible, from the most recent start date
     * to the oldest. Always two statements, the page and the total, so the number of statements never depends on the
     * results.
     */
    Page<TreatmentListRow> findReportPage(ReportCriteria criteria, Pageable pageable);

    /** Every treatment of the criteria, in the order of {@link #findReportPage}, in one statement (HU-15: the PDF). */
    List<TreatmentListRow> findReportRows(ReportCriteria criteria);

}
