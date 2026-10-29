package ec.rehabelite.auth.application;

import ec.rehabelite.auth.Role;
import java.time.Instant;

/**
 * Outcome of a successful login: the signed access token and the data of the authenticated user.
 */
public record LoginResult(String token, Instant expiresAt, Long userId, String email, String fullName, Role role) {
}
