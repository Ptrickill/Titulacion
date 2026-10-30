package ec.rehabelite.audit;

import java.util.Collection;

/**
 * Public API of the audit module: appends an entry to the append-only audit log.
 * <p>
 * Each entry stores the acting user, the action, the affected entity, the time and the client IP of the
 * current HTTP request. Callers pass only identifiers, never personal or clinical data.
 */
public interface AuditLogService {

    /**
     * Records an action performed by the authenticated user of the current request (none if anonymous).
     *
     * @param entityType name of the affected entity, e.g. {@code "Patient"}
     * @param entityId   id of the affected entity, or {@code null} when it does not apply
     */
    void record(AuditAction action, String entityType, Long entityId);

    /**
     * Records the same action on several entities, one entry each (e.g. the clinical records shown in a list).
     * Nothing is recorded for an empty collection.
     */
    void recordAll(AuditAction action, String entityType, Collection<Long> entityIds);

    /**
     * Records an action on behalf of a given user, for requests that are not authenticated yet
     * (e.g. a successful login).
     *
     * @param userId acting user, or {@code null} when unknown
     */
    void recordFor(Long userId, AuditAction action, String entityType, Long entityId);

}
