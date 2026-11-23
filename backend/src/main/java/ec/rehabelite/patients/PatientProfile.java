package ec.rehabelite.patients;

import java.time.LocalDate;
import java.time.Period;

/**
 * Personal and contact data of a patient for the full clinical record view (HU-09). Ethnic self-identification and
 * religion are left out: no other module needs them.
 *
 * @param sex {@code MALE} or {@code FEMALE}
 * @param address the composed address (MEJ-03), with the place names of the INEC catalog; for a patient registered
 *                before MEJ-03, its former free text
 * @param addressComplete whether the address has its place and streets (false until an old patient is edited)
 * @param hasHealthInsurance "¿Tiene seguro de salud?" (MEJ-03)
 */
public record PatientProfile(
        Long id,
        String nationalId,
        String medicalRecordNumber,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String sex,
        String phone,
        String email,
        String address,
        boolean addressComplete,
        String occupation,
        String educationLevel,
        boolean hasHealthInsurance,
        LocalDate admissionDate) {

    /** Completed years on the given date; the age is never stored. */
    public int ageOn(LocalDate date) {
        return Period.between(birthDate, date).getYears();
    }

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "PatientProfile[id=" + id + "]";
    }

}
