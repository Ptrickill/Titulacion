package ec.rehabelite.clinicalrecords.domain;

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

    private final EntityManager entityManager;

    TreatmentReportQueriesImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Map<TreatmentStatus, Long> countByStatus(TreatmentStatus status, Long responsibleId) {
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT t.status, COUNT(t) FROM Treatment t" + CURRENT_RESPONSIBLE + where(status, responsibleId)
                        + " GROUP BY t.status", Object[].class);
        bind(query, status, responsibleId);
        Map<TreatmentStatus, Long> counts = new EnumMap<>(TreatmentStatus.class);
        query.getResultList().forEach(row -> counts.put((TreatmentStatus) row[0], ((Number) row[1]).longValue()));
        return counts;
    }

    @Override
    public List<Long> findIdsByStatus(TreatmentStatus status) {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT t.id FROM Treatment t" + where(status, null), Long.class);
        bind(query, status, null);
        return query.getResultList();
    }

    @Override
    public Page<TreatmentListRow> findReportPage(TreatmentStatus status, Long responsibleId, Pageable pageable) {
        String where = where(status, responsibleId);
        TypedQuery<TreatmentListRow> select = entityManager.createQuery("""
                SELECT new ec.rehabelite.clinicalrecords.domain.TreatmentListRow(t.id, r.patientId, t.status,
                       t.startDate, t.initialPainScore, t.plannedSessions, d.code, d.description, a.physiotherapistId)
                FROM Treatment t JOIN t.clinicalRecord r JOIN t.diagnosis d""" + CURRENT_RESPONSIBLE + where
                + " ORDER BY t.startDate DESC, t.id DESC", TreatmentListRow.class);
        bind(select, status, responsibleId);
        select.setFirstResult((int) pageable.getOffset());
        select.setMaxResults(pageable.getPageSize());
        List<TreatmentListRow> content = select.getResultList();

        TypedQuery<Long> count = entityManager.createQuery(
                "SELECT COUNT(t) FROM Treatment t" + CURRENT_RESPONSIBLE + where, Long.class);
        bind(count, status, responsibleId);
        return new PageImpl<>(content, pageable, count.getSingleResult());
    }

    /** The responsible condition needs the CURRENT_RESPONSIBLE join. */
    private static String where(TreatmentStatus status, Long responsibleId) {
        List<String> conditions = new ArrayList<>();
        if (status != null) {
            conditions.add("t.status = :status");
        }
        if (responsibleId != null) {
            conditions.add("a.physiotherapistId = :responsibleId");
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static void bind(Query query, TreatmentStatus status, Long responsibleId) {
        if (status != null) {
            query.setParameter("status", status);
        }
        if (responsibleId != null) {
            query.setParameter("responsibleId", responsibleId);
        }
    }

}
