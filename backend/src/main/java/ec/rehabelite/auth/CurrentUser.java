package ec.rehabelite.auth;

/**
 * Authenticated user of the current request, as stated by the JWT claims.
 */
public record CurrentUser(Long id, String email, String fullName, Role role) {
}
