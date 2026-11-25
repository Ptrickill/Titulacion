package ec.rehabelite.audit.application;

import ec.rehabelite.audit.AuditUser;
import ec.rehabelite.audit.AuditUserDirectory;
import ec.rehabelite.audit.domain.AuditLog;
import ec.rehabelite.audit.domain.AuditLogFilter;
import ec.rehabelite.audit.domain.AuditLogRepository;
import ec.rehabelite.audit.domain.AuditLogScope;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only access to the audit log (HU-18): metadata only (who, what, which entity, when, from where), never clinical
 * data. The operations director reads all of it and the reception only the entries of the patients and of their
 * portal accounts (MEJ-05, {@link AuditLogScope}). Reading the log is not itself audited.
 */
@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    /** Most recent first; the id breaks ties between entries of the same instant. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final AuditLogRepository auditLogRepository;
    private final AuditUserDirectory userDirectory;

    /** A page of entries with the names of their users (null for anonymous entries or unknown users). */
    public record AuditLogPage(Page<AuditLog> entries, Map<Long, String> userNames) {

        public String nameOf(Long userId) {
            return userId == null ? null : userNames.get(userId);
        }

    }

    /** The whole log, or only the patients' part for the reception (MEJ-05). */
    @Transactional(readOnly = true)
    public AuditLogScope scopeFor(boolean reception) {
        return reception ? AuditLogScope.patients(userDirectory.patientAccountIds()) : AuditLogScope.everything();
    }

    @Transactional(readOnly = true)
    public AuditLogPage search(AuditLogFilter filter, AuditLogScope scope, int page, int size) {
        Page<AuditLog> entries = auditLogRepository.findAll(scope.toSpecification().and(filter.toSpecification()),
                PageRequest.of(page, size, NEWEST_FIRST));
        List<Long> userIds = entries.getContent().stream()
                .map(AuditLog::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new AuditLogPage(entries, userDirectory.fullNames(userIds));
    }

    /** The entity types of the log, for the entity filter; for the reception only Patient and User. */
    @Transactional(readOnly = true)
    public List<String> entityTypes(AuditLogScope scope) {
        List<String> types = auditLogRepository.findDistinctEntityTypes();
        return scope.patientsOnly()
                ? types.stream().filter(AuditLogScope.PATIENT_ENTITY_TYPES::contains).toList()
                : types;
    }

    /**
     * The users of the user filter: every account for the operations director; for the reception only the users that
     * appear in the entries it may read.
     */
    @Transactional(readOnly = true)
    public List<AuditUser> users(AuditLogScope scope) {
        List<AuditUser> users = userDirectory.users();
        if (!scope.patientsOnly()) {
            return users;
        }
        Set<Long> authors = Set.copyOf(scope.patientAccountIds().isEmpty()
                ? auditLogRepository.findDistinctUserIdsOfEntity("Patient")
                : auditLogRepository.findDistinctUserIdsOfPatients(scope.patientAccountIds()));
        return users.stream().filter(user -> authors.contains(user.id())).toList();
    }

}
