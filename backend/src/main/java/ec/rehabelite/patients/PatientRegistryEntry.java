package ec.rehabelite.patients;

/**
 * A patient in the people registry (MEJ-02): identification and contact, whether data is still to be completed and
 * the portal account, if any. Personal data only, never clinical data. MEJ-06: {@code blocked} by an accepted erasure
 * request; such a patient has nothing to complete (its contact data was cleared on purpose).
 */
public record PatientRegistryEntry(
        Long id,
        String nationalId,
        String firstName,
        String lastName,
        String email,
        String phone,
        boolean needsCompletion,
        Long portalUserId,
        boolean blocked) {

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "PatientRegistryEntry[id=" + id + "]";
    }

}
