package ec.rehabelite.auth;

import java.time.Instant;

/**
 * What the people registry shows of an account (MEJ-02): its id, role and status, whether a change of email is
 * waiting for its owner's confirmation, and the last login (null if it never logged in). Never the email nor the
 * password.
 */
public record AccountSummary(Long userId, Role role, AccountStatus status, boolean emailChangePending,
        Instant lastLoginAt) {
}
