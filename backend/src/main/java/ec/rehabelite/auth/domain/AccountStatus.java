package ec.rehabelite.auth.domain;

/** Status of an account as the administrator sees it (MEJ-01). */
public enum AccountStatus {
    /** Created, but its owner has not followed the activation link yet. */
    PENDING_ACTIVATION,
    /** Activated and enabled. */
    ACTIVE,
    /** Deactivated by an administrator (HU-03). */
    INACTIVE
}
