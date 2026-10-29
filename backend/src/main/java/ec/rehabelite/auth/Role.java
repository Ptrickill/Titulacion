package ec.rehabelite.auth;

/**
 * User role (same values as the ck_users_role constraint). Spring Security authorities are
 * {@code ROLE_<name>}, e.g. {@code ROLE_PHYSIOTHERAPIST}.
 */
public enum Role {
    ADMINISTRATOR,
    PHYSIOTHERAPIST,
    PATIENT,
    OPERATIONS_DIRECTOR
}
