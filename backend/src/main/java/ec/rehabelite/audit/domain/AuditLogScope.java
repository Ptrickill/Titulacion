package ec.rehabelite.audit.domain;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/**
 * Which part of the audit log a role may read (MEJ-05). The operations director reads all of it. The reception reads
 * only the entries of the patients ({@code Patient}) and of their portal accounts ({@code User} entries whose id is a
 * patient account): least privilege, since it manages only patients. The scope is combined with the filters of the
 * request, so no filter can widen it.
 */
public record AuditLogScope(boolean patientsOnly, List<Long> patientAccountIds) {

    static final String PATIENT_ENTITY = "Patient";
    static final String USER_ENTITY = "User";
    /** The entity types the reception may see. */
    public static final Set<String> PATIENT_ENTITY_TYPES = Set.of(PATIENT_ENTITY, USER_ENTITY);

    public AuditLogScope {
        patientAccountIds = List.copyOf(patientAccountIds);
    }

    public static AuditLogScope everything() {
        return new AuditLogScope(false, List.of());
    }

    public static AuditLogScope patients(Collection<Long> patientAccountIds) {
        return new AuditLogScope(true, List.copyOf(patientAccountIds));
    }

    public Specification<AuditLog> toSpecification() {
        if (!patientsOnly) {
            return (root, query, builder) -> builder.conjunction();
        }
        return (root, query, builder) -> {
            var patient = builder.equal(root.get("entityType"), PATIENT_ENTITY);
            if (patientAccountIds.isEmpty()) {
                return patient;
            }
            return builder.or(patient, builder.and(builder.equal(root.get("entityType"), USER_ENTITY),
                    root.get("entityId").in(patientAccountIds)));
        };
    }

}
