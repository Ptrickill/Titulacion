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
    LOGIN,
    /** A report exported to a file (HU-15: the PDF of the operations report). Added by V4. */
    EXPORT
}
