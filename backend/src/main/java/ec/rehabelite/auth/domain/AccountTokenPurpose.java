package ec.rehabelite.auth.domain;

/** What a one-time link is for (MEJ-01); same values as ck_account_tokens_purpose. */
public enum AccountTokenPurpose {
    /** Sent when the account is created or the invitation is resent: sets the first password. */
    ACTIVATION,
    /** Sent on request ("¿Olvidó su contraseña?"): sets a new password. */
    PASSWORD_RESET
}
