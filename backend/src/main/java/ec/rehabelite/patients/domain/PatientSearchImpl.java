package ec.rehabelite.patients.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Arrays;
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
        List<String> parameters = new ArrayList<>();
        String where = whereClause(sanitize(query), parameters);

        Query select = entityManager.createNativeQuery(
                "SELECT p.* FROM patients p" + where + ORDER_BY, Patient.class);
        bind(select, parameters);
        select.setFirstResult((int) pageable.getOffset());
        select.setMaxResults(pageable.getPageSize());
        @SuppressWarnings("unchecked")
        List<Patient> content = select.getResultList();

        Query count = entityManager.createNativeQuery("SELECT count(*) FROM patients p" + where);
        bind(count, parameters);
        long total = ((Number) count.getSingleResult()).longValue();
        return new PageImpl<>(content, pageable, total);
    }

    private static String whereClause(String query, List<String> parameters) {
        if (query.isEmpty()) {
            return "";
        }
        if (query.chars().allMatch(c -> c >= '0' && c <= '9')) {
            parameters.add(query);
            return " WHERE p.national_id LIKE ?1 || '%'";
        }
        List<String> conditions = new ArrayList<>();
        Arrays.stream(query.split("\\s+")).limit(MAX_WORDS).forEach(word -> {
            parameters.add(word);
            conditions.add(NAME + " LIKE '%' || normalize_text(?" + parameters.size() + ") || '%'");
        });
        return " WHERE " + String.join(" AND ", conditions);
    }

    private static void bind(Query query, List<String> parameters) {
        for (int i = 0; i < parameters.size(); i++) {
            query.setParameter(i + 1, parameters.get(i));
        }
    }

    /** LIKE wildcards typed by the user are removed so they match literally nothing special. */
    private static String sanitize(String query) {
        return query == null ? "" : query.replaceAll("[%_\\\\]", "").strip();
    }

}
