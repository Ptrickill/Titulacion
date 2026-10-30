package ec.rehabelite.patients;

import java.time.LocalDate;
import java.time.Period;

/**
 * Identification of a registered patient, for other modules (e.g. the physiotherapist's patient list). Personal
 * data only; contact data stays in the patients module.
 */
public record PatientCard(
        Long id,
        String nationalId,
        String medicalRecordNumber,
        String firstName,
        String lastName,
        LocalDate birthDate) {

    /** Completed years on the given date; the age is never stored. */
    public int ageOn(LocalDate date) {
        return Period.between(birthDate, date).getYears();
    }

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "PatientCard[id=" + id + "]";
    }

}
