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
    EXPORT,
    /** MEJ-01: activation link emailed when the account is created. Added by V5, like the four below. */
    INVITATION_SENT,
    /** MEJ-01: a new activation link sent by the administrator to a pending account. */
    INVITATION_RESENT,
    /** MEJ-01: the owner activated the account and set the password. */
    ACTIVATION,
    /** MEJ-01: password reset requested (with the account id, or without it for an unknown email). */
    RESET_REQUEST,
    /** MEJ-01: password changed through a reset link. */
    PASSWORD_RESET,
    /**
     * MEJ-02: the email of an account was changed by editing its person; neither address is recorded. Added by V7,
     * like the one below.
     */
    EMAIL_CHANGE,
    /** MEJ-02: the owner confirmed the new email through the emailed link. */
    EMAIL_CONFIRMATION
}
