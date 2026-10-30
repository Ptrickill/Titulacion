package ec.rehabelite.audit.application;

import ec.rehabelite.audit.AuditUser;
import ec.rehabelite.audit.AuditUserDirectory;
import ec.rehabelite.audit.domain.AuditLog;
import ec.rehabelite.audit.domain.AuditLogFilter;
import ec.rehabelite.audit.domain.AuditLogRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only access to the audit log for the administrator and the operations director (HU-18): metadata only
 * (who, what, which entity, when, from where), never clinical data. Reading the log is not itself audited.
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

    @Transactional(readOnly = true)
    public AuditLogPage search(AuditLogFilter filter, int page, int size) {
        Page<AuditLog> entries = auditLogRepository.findAll(filter.toSpecification(),
                PageRequest.of(page, size, NEWEST_FIRST));
        List<Long> userIds = entries.getContent().stream()
                .map(AuditLog::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new AuditLogPage(entries, userDirectory.fullNames(userIds));
    }

    @Transactional(readOnly = true)
    public List<String> entityTypes() {
        return auditLogRepository.findDistinctEntityTypes();
    }

    public List<AuditUser> users() {
        return userDirectory.users();
    }

}
