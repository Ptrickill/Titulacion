package ec.rehabelite.auth.mail;

/** Kind of an email, which decides whether it needs the recipient's consent (MEJ-06). */
public enum MailCategory {
    /**
     * Needed for the account: activation, password reset, email confirmation and the notice to the previous address.
     * Always sent, whatever the consents.
     */
    ACCOUNT,
    /**
     * Not needed for the account (e.g. future reminders): sent only to an account that accepts communications
     * ({@code MailConsentPolicy}). None exists today.
     */
    COMMUNICATION
}
