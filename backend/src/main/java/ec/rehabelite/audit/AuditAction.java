package ec.rehabelite.audit;

/**
 * Action recorded in {@code audit_logs.action} (same values as the ck_audit_logs_action constraint).
 */
public enum AuditAction {
    CREATE,
    READ,
    UPDATE,
    STATUS_CHANGE,
    ACCESS_DENIED,
    LOGIN
}
