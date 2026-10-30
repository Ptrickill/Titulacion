package ec.rehabelite.audit.domain;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.shared.domain.ClinicCalendar;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters of the audit log (HU-18); every one is optional. The dates are calendar days in Quito and both ends are
 * included: {@code to} runs until the start of the next day.
 */
public record AuditLogFilter(Long userId, AuditAction action, String entityType, Long entityId, LocalDate from,
        LocalDate to) {

    public static AuditLogFilter none() {
        return new AuditLogFilter(null, null, null, null, null, null);
    }

    public Specification<AuditLog> toSpecification() {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(builder.equal(root.get("userId"), userId));
            }
            if (action != null) {
                predicates.add(builder.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(builder.equal(root.get("entityType"), entityType.strip()));
            }
            if (entityId != null) {
                predicates.add(builder.equal(root.get("entityId"), entityId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), startOf(from)));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.<Instant>get("occurredAt"), startOf(to.plusDays(1))));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Midnight of the day in Quito, as an instant. */
    static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ClinicCalendar.ZONE).toInstant();
    }

}
