package ec.rehabelite.patients.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Builds one condition per word on {@code normalize_text(last_name || ' ' || first_name)}, the exact expression of
 * the trigram index ix_patients_full_name_trgm (V1), so PostgreSQL can combine the index scans of every word.
 * Words are passed as bind parameters, never concatenated into the SQL.
 */
class PatientSearchImpl implements PatientSearch {

    /** More words than this add nothing to a name search and only make the query longer. */
    static final int MAX_WORDS = 5;

    private static final String NAME = "normalize_text(p.last_name || ' ' || p.first_name)";
    /** By last name, then first name; normalized so "Álvarez" sorts with the A whatever the database collation. */
    private static final String ORDER_BY =
            " ORDER BY normalize_text(p.last_name), normalize_text(p.first_name), p.id";

    private final EntityManager entityManager;

    PatientSearchImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<Patient> search(String query, Pageable pageable) {
        List<Patient> content = searchSlice(query, false, null, pageable.getOffset(), pageable.getPageSize());
        return new PageImpl<>(content, pageable, countSearch(query, false, null));
    }

    @Override
    public List<Patient> searchSlice(String query, boolean withoutPortalAccount, Collection<Long> portalUserIds,
            long offset, int limit) {
        List<Object> parameters = new ArrayList<>();
        String where = whereClause(sanitize(query), withoutPortalAccount, portalUserIds, parameters);
        Query select = entityManager.createNativeQuery(
                "SELECT p.* FROM patients p" + where + ORDER_BY, Patient.class);
        bind(select, parameters);
        select.setFirstResult((int) offset);
        select.setMaxResults(limit);
        @SuppressWarnings("unchecked")
        List<Patient> content = select.getResultList();
        return content;
    }

    @Override
    public long countSearch(String query, boolean withoutPortalAccount, Collection<Long> portalUserIds) {
        List<Object> parameters = new ArrayList<>();
        String where = whereClause(sanitize(query), withoutPortalAccount, portalUserIds, parameters);
        Query count = entityManager.createNativeQuery("SELECT count(*) FROM patients p" + where);
        bind(count, parameters);
        return ((Number) count.getSingleResult()).longValue();
    }

    /** Only the criteria sent become conditions; the account ids go as one array parameter. */
    private static String whereClause(String query, boolean withoutPortalAccount, Collection<Long> portalUserIds,
            List<Object> parameters) {
        List<String> conditions = new ArrayList<>();
        if (withoutPortalAccount) {
            conditions.add("p.portal_user_id IS NULL");
        } else if (portalUserIds != null) {
            parameters.add(portalUserIds.toArray(Long[]::new));
            conditions.add("p.portal_user_id = ANY (CAST(?" + parameters.size() + " AS bigint[]))");
        }
        if (!query.isEmpty()) {
            if (query.chars().allMatch(c -> c >= '0' && c <= '9')) {
                parameters.add(query);
                conditions.add("p.national_id LIKE ?" + parameters.size() + " || '%'");
            } else {
                Arrays.stream(query.split("\\s+")).limit(MAX_WORDS).forEach(word -> {
                    parameters.add(word);
                    conditions.add(NAME + " LIKE '%' || normalize_text(?" + parameters.size() + ") || '%'");
                });
            }
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static void bind(Query query, List<Object> parameters) {
        for (int i = 0; i < parameters.size(); i++) {
            query.setParameter(i + 1, parameters.get(i));
        }
    }
    private static String sanitize(String query) {
        return query == null ? "" : query.replaceAll("[%_\\\\]", "").strip();
    }

}
