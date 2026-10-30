package ec.rehabelite.audit;

/** A user that can appear in the audit log: id and full name only. */
public record AuditUser(Long id, String fullName) {
}
