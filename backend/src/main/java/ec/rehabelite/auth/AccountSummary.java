package ec.rehabelite.auth;

/**
 * What the people registry shows of an account (MEJ-02): its id, role and status, and whether a change of email is
 * waiting for its owner's confirmation. Never the email nor the password.
 */
public record AccountSummary(Long userId, Role role, AccountStatus status, boolean emailChangePending) {
}
