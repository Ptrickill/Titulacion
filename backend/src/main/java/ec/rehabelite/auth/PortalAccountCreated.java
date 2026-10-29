package ec.rehabelite.auth;

/**
 * Published inside the transaction that creates a PATIENT account. The patients module listens
 * synchronously and links the account to the patient; if the link is not possible its exception rolls
 * back the account creation, so a portal account never exists without its patient.
 */
public record PortalAccountCreated(Long userId, Long patientId) {
}
