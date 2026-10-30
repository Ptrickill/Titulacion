package ec.rehabelite.patients;

import java.time.LocalDate;
import java.time.Period;

/**
 * Personal and contact data of a patient for the full clinical record view (HU-09). Ethnic self-identification and
 * religion are left out: no other module needs them.
 *
 * @param sex {@code MALE} or {@code FEMALE}
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
        String occupation,
        String educationLevel,
        String healthInsurance,
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
