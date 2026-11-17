package ec.rehabelite.clinicalrecords.domain;

import ec.rehabelite.shared.domain.ClinicCalendar;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Builds the JPQL of {@link TreatmentReportQueries} with only the criteria that were sent: PostgreSQL cannot type a
 * null parameter in {@code :param IS NULL}. Values are always bind parameters, never concatenated into the query.
 */
class TreatmentReportQueriesImpl implements TreatmentReportQueries {

    /** At most one current assignment per treatment (uq_responsible_assignments_current), so no row is repeated. */
    private static final String CURRENT_RESPONSIBLE =
            " LEFT JOIN ResponsibleAssignment a ON a.treatment = t AND a.validTo IS NULL";

    private static final String ROW = """
            SELECT new ec.rehabelite.clinicalrecords.domain.TreatmentListRow(t.id, r.patientId, t.status,
                   t.startDate, t.initialPainScore, t.plannedSessions, d.code, d.description, a.physiotherapistId)
            FROM Treatment t JOIN t.clinicalRecord r JOIN t.diagnosis d""" + CURRENT_RESPONSIBLE;

    private static final String ORDER = " ORDER BY t.startDate DESC, t.id DESC";

    private final EntityManager entityManager;

    TreatmentReportQueriesImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Map<TreatmentStatus, Long> countByStatus(ReportCriteria criteria) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT t.status, COUNT(t) FROM Treatment t" + CURRENT_RESPONSIBLE + where(criteria)
                        + " GROUP BY t.status", Object[].class);
        bind(query, criteria);
        Map<TreatmentStatus, Long> counts = new EnumMap<>(TreatmentStatus.class);
        query.getResultList().forEach(row -> counts.put((TreatmentStatus) row[0], ((Number) row[1]).longValue()));
        return counts;
    }

    @Override
    public List<Long> findIdsByStatus(TreatmentStatus status) {
        ReportCriteria criteria = new ReportCriteria(status, null, null, null);
        TypedQuery<Long> query = entityManager.createQuery("SELECT t.id FROM Treatment t" + where(criteria),
                Long.class);
        bind(query, criteria);
        return query.getResultList();
    }

    @Override
    public Page<TreatmentListRow> findReportPage(ReportCriteria criteria, Pageable pageable) {
        String where = where(criteria);
        TypedQuery<TreatmentListRow> select = entityManager.createQuery(ROW + where + ORDER, TreatmentListRow.class);
        bind(select, criteria);
        select.setFirstResult((int) pageable.getOffset());
        select.setMaxResults(pageable.getPageSize());
        List<TreatmentListRow> content = select.getResultList();

        TypedQuery<Long> count = entityManager.createQuery(
                "SELECT COUNT(t) FROM Treatment t" + CURRENT_RESPONSIBLE + where, Long.class);
        bind(count, criteria);
        return new PageImpl<>(content, pageable, count.getSingleResult());
    }

    @Override
    public List<TreatmentListRow> findReportRows(ReportCriteria criteria) {
        TypedQuery<TreatmentListRow> select = entityManager.createQuery(ROW + where(criteria) + ORDER,
                TreatmentListRow.class);
        bind(select, criteria);
        return select.getResultList();
    }

    /**
     * The responsible condition needs the CURRENT_RESPONSIBLE join. With {@code from}, the treatment is kept if it is
     * still active, if it started on or after that day, or if it changed status (closed) on or after the first
     * instant of that day in Quito. "Some change on or after" equals "the last change on or after", and the last
     * change of a closed treatment is its closure; the start date also covers a closed treatment without changes.
     */
    private static String where(ReportCriteria criteria) {
        List<String> conditions = new ArrayList<>();
        if (criteria.status() != null) {
            conditions.add("t.status = :status");
        }
        if (criteria.responsibleId() != null) {
            conditions.add("a.physiotherapistId = :responsibleId");
        }
        if (criteria.to() != null) {
            conditions.add("t.startDate <= :to");
        }
        if (criteria.from() != null) {
            conditions.add("""
                    (t.status = :active OR t.startDate >= :from OR EXISTS (SELECT c.id FROM TreatmentStatusChange c
                     WHERE c.treatment = t AND c.changedAt >= :fromInstant))""");
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static void bind(Query query, ReportCriteria criteria) {
        if (criteria.status() != null) {
            query.setParameter("status", criteria.status());
        }
        if (criteria.responsibleId() != null) {
            query.setParameter("responsibleId", criteria.responsibleId());
        }
        if (criteria.to() != null) {
            query.setParameter("to", criteria.to());
        }
        if (criteria.from() != null) {
            query.setParameter("active", TreatmentStatus.ACTIVE);
            query.setParameter("from", criteria.from());
            query.setParameter("fromInstant", criteria.from().atStartOfDay(ClinicCalendar.ZONE).toInstant());
        }
    }

}
