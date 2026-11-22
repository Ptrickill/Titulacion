package ec.rehabelite.auth.application;

import ec.rehabelite.auth.Role;

/**
 * Data of a new account. {@code patientId} is required for (and only allowed with) the PATIENT role. There is no
 * password: the owner sets it through the activation link (MEJ-01).
 */
public record CreateUserCommand(String email, String fullName, Role role, Long patientId) {
}
