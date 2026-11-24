package ec.rehabelite.patients;

import java.util.Optional;

/**
 * National IDs and emails registered outside the patients module (MEJ-02): the staff records. The patients module
 * declares it and the staff module implements it, so the national ID and the staff email stay unique between
 * patients and staff without the patients module ever depending on the staff module (the same pattern as
 * {@code audit.AuditUserDirectory}).
 */
public interface RegisteredIdentities {

    /** The staff record with that national ID, if any. */
    Optional<Long> staffWithNationalId(String nationalId);

    /** The staff record with that email (already normalized: trimmed and in lower case), if any. */
    Optional<Long> staffWithEmail(String email);

}
