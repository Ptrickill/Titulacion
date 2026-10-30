package ec.rehabelite.patients.domain;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Personal (non-clinical) data of a patient. Text is stored trimmed, the email in lower case, and blank optional
 * values (ethnic self-identification, religion) as null.
 */
public record PersonalData(
        String firstName,
        String lastName,
        LocalDate birthDate,
        Sex sex,
        String phone,
        String email,
        String address,
        String occupation,
        String educationLevel,
        String healthInsurance,
        String ethnicSelfIdentification,
        String religion) {

    public PersonalData {
        firstName = strip(firstName);
        lastName = strip(lastName);
        phone = strip(phone);
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        address = strip(address);
        occupation = strip(occupation);
        educationLevel = strip(educationLevel);
        healthInsurance = strip(healthInsurance);
        ethnicSelfIdentification = blankToNull(ethnicSelfIdentification);
        religion = blankToNull(religion);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "PersonalData[...]";
    }

}
