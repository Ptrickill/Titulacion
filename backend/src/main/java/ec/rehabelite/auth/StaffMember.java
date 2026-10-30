package ec.rehabelite.auth;

/** A staff account as other modules see it: its user id and full name. */
public record StaffMember(Long id, String fullName) {
}
