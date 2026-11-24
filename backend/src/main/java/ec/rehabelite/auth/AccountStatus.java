package ec.rehabelite.auth;

/** Status of an account as the administrator sees it (MEJ-01); public since MEJ-02 (see {@link AccountSummary}). */
public enum AccountStatus {
    /** Created, but its owner has not followed the activation link yet. */
    PENDING_ACTIVATION,
    /** Activated and enabled. */
    ACTIVE,
    /** Deactivated by an administrator (HU-03). */
    INACTIVE
}
