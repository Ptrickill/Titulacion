package ec.rehabelite.staff.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Builds the WHERE clause with only the criteria sent. Words and ids are bind parameters, never concatenated into the
 * SQL; the account ids go as one array ({@code = ANY(CAST(? AS bigint[]))}).
 */
class StaffSearchImpl implements StaffSearch {

    /** More words than this add nothing to a name search and only make the query longer. */
    static final int MAX_WORDS = 5;

    private static final String NAME = "normalize_text(s.last_name || ' ' || s.first_name)";
    private static final String ORDER_BY =
            " ORDER BY normalize_text(s.last_name), normalize_text(s.first_name), s.id";

    private final EntityManager entityManager;

    StaffSearchImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<StaffRecord> searchSlice(String query, Collection<Long> userIds, long offset, int limit) {
        List<Object> parameters = new ArrayList<>();
        String where = whereClause(sanitize(query), userIds, parameters);
        Query select = entityManager.createNativeQuery(
                "SELECT s.* FROM staff_records s" + where + ORDER_BY, StaffRecord.class);
        bind(select, parameters);
        select.setFirstResult((int) offset);
        select.setMaxResults(limit);
        @SuppressWarnings("unchecked")
        List<StaffRecord> content = select.getResultList();
        return content;
    }

    @Override
    public long countSearch(String query, Collection<Long> userIds) {
        List<Object> parameters = new ArrayList<>();
        String where = whereClause(sanitize(query), userIds, parameters);
        Query count = entityManager.createNativeQuery("SELECT count(*) FROM staff_records s" + where);
        bind(count, parameters);
        return ((Number) count.getSingleResult()).longValue();
    }

    private static String whereClause(String query, Collection<Long> userIds, List<Object> parameters) {
        List<String> conditions = new ArrayList<>();
        if (userIds != null) {
            parameters.add(userIds.toArray(Long[]::new));
            conditions.add("s.user_id = ANY (CAST(?" + parameters.size() + " AS bigint[]))");
        }
        if (!query.isEmpty()) {
            if (query.chars().allMatch(c -> c >= '0' && c <= '9')) {
                parameters.add(query);
                conditions.add("s.national_id LIKE ?" + parameters.size() + " || '%'");
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

    /** LIKE wildcards typed by the user are removed so they match literally nothing special. */
    private static String sanitize(String query) {
        return query == null ? "" : query.replaceAll("[%_\\\\]", "").strip();
    }

}
