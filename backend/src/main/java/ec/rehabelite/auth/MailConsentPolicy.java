package ec.rehabelite.auth;

/**
 * Whether an account accepts non-essential email communications (MEJ-06). Declared here and implemented by the
 * patients module, which owns the consents, so auth never depends on patients (like {@code AuditUserDirectory}).
 * <p>
 * The account emails (activation, password reset, email confirmation and the notice to the previous address) are
 * essential and never ask this policy: a patient with portal access always gets them. Only the communications that
 * are not needed for the account, none today (future reminders), require it.
 */
public interface MailConsentPolicy {

    /**
     * True for a staff account, or for a portal account whose patient has a valid EMAIL_COMMUNICATIONS consent and is
     * not blocked.
     */
    boolean allowsCommunications(Long userId);

}
