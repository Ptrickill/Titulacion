package ec.rehabelite.patients.domain;

import ec.rehabelite.shared.domain.NameCase;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Personal (non-clinical) data of a patient. Names and surnames are stored without extra spaces and with a capital
 * initial (MEJ-03, {@link NameCase}), text is stored trimmed, the email in lower case, and blank optional values
 * (ethnic self-identification, religion) as null.
 */
public record PersonalData(
        String firstName,
        String lastName,
        LocalDate birthDate,
        Sex sex,
        String phone,
        String email,
        PatientAddress address,
        String occupation,
        String educationLevel,
        boolean hasHealthInsurance,
        String ethnicSelfIdentification,
        String religion) {

    public PersonalData {
        firstName = NameCase.of(firstName);
        lastName = NameCase.of(lastName);
        phone = strip(phone);
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        occupation = strip(occupation);
        educationLevel = strip(educationLevel);
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
