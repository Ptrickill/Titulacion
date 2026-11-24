package ec.rehabelite.shared.domain;

import java.util.regex.Pattern;

/**
 * Ecuadorian phone number of a patient or a staff member (MEJ-03, MEJ-02): a mobile, 09 and 8 digits ("0991234567"), or a landline, 0, the
 * province area code from 2 to 7 and 7 digits ("022345678"). Digits only. The frontend applies the same rule
 * ({@code shared/forms/phone.ts}).
 */
public final class PhoneNumber {

    public static final String MESSAGE =
            "Ingrese un celular (09XXXXXXXX) o un teléfono fijo con código de provincia";

    private static final Pattern VALID = Pattern.compile("^(09\\d{8}|0[2-7]\\d{7})$");

    private PhoneNumber() {
    }

    public static boolean isValid(String phone) {
        return phone != null && VALID.matcher(phone).matches();
    }

}
