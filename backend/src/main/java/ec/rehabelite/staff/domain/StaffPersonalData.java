package ec.rehabelite.staff.domain;

import ec.rehabelite.shared.domain.NameCase;
import ec.rehabelite.shared.domain.PostalAddress;
import ec.rehabelite.shared.domain.Sex;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Personal data of a staff member (MEJ-02): the same personal data as a patient, without the admission data. Names
 * are stored without extra spaces and with a capital initial ({@link NameCase}), the phone trimmed and the email in
 * lower case.
 */
public record StaffPersonalData(
        String firstName,
        String lastName,
        LocalDate birthDate,
        Sex sex,
        String phone,
        String email,
        PostalAddress address) {

    public StaffPersonalData {
        firstName = NameCase.of(firstName);
        lastName = NameCase.of(lastName);
        phone = phone == null ? null : phone.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /** "First names last names": the name of the account (MEJ-02: always derived from the record). */
    public String fullName() {
        return (firstName + " " + lastName).strip();
    }

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "StaffPersonalData[...]";
    }

}
