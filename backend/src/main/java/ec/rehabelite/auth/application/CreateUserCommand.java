package ec.rehabelite.auth.application;

import ec.rehabelite.auth.Role;

/**
 * Data of a new account. {@code patientId} is required for (and only allowed with) the PATIENT role.
 */
public record CreateUserCommand(String email, String fullName, Role role, String password, Long patientId) {

    /** Never print the password, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "CreateUserCommand[role=" + role + ", patientId=" + patientId + "]";
    }

}
