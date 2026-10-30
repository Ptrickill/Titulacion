package ec.rehabelite.patients.domain;

/**
 * Ecuadorian national ID (cédula) of a natural person: 10 digits, province code 01–24 or 30 (Ecuadorians
 * registered abroad), third digit 0–5 and a modulo 10 check digit.
 */
public final class NationalId {

    public static final int LENGTH = 10;

    private static final int MAX_PROVINCE = 24;
    private static final int ABROAD_PROVINCE = 30;
    private static final int MAX_NATURAL_PERSON_DIGIT = 5;

    private NationalId() {
    }

    public static boolean isValid(String value) {
        if (value == null || value.length() != LENGTH || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        int province = Integer.parseInt(value.substring(0, 2));
        if ((province < 1 || province > MAX_PROVINCE) && province != ABROAD_PROVINCE) {
            return false;
        }
        if (digitAt(value, 2) > MAX_NATURAL_PERSON_DIGIT) {
            return false;
        }
        return checkDigit(value) == digitAt(value, LENGTH - 1);
    }

    /**
     * Modulo 10: the first nine digits are multiplied by 2, 1, 2, 1, ...; products above 9 lose 9; the check
     * digit is what takes the sum to the next multiple of 10 (0 when the sum already is one).
     */
    static int checkDigit(String value) {
        int sum = 0;
        for (int i = 0; i < LENGTH - 1; i++) {
            int product = digitAt(value, i) * (i % 2 == 0 ? 2 : 1);
            sum += product > 9 ? product - 9 : product;
        }
        return (10 - sum % 10) % 10;
    }

    private static int digitAt(String value, int index) {
        return value.charAt(index) - '0';
    }

}
