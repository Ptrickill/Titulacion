package ec.rehabelite.patients;

/**
 * A patient in the people registry (MEJ-02): identification and contact, whether data is still to be completed and
 * the portal account, if any. Personal data only, never clinical data.
 */
public record PatientRegistryEntry(
        Long id,
        String nationalId,
        String firstName,
        String lastName,
        String email,
        String phone,
        boolean needsCompletion,
        Long portalUserId) {

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "PatientRegistryEntry[id=" + id + "]";
    }

}
