package ec.rehabelite.patients;

/** How a consent was given or revoked (MEJ-06). */
public enum ConsentChannel {
    /** Signed on paper at the reception, which records it. */
    PHYSICAL_SIGNATURE,
    /** Accepted or revoked by the patient in the portal. */
    PORTAL,
    /** Revoked by an accepted erasure request; never a way to give a consent. */
    ERASURE
}
