package ec.rehabelite.staff.domain;

import java.util.Arrays;

/**
 * Splits the single full name of an account created before MEJ-02 (and of the initial administrator) into first
 * names and last names, with the same rule as V7: one word is the first name (no last name); two words, one each;
 * three words, the first one and the last two; four or more, the first two and the rest. The words are kept as
 * written, so "first last" is the account name again. The record shows "Datos por completar" and the administrator
 * can correct the names when completing it.
 */
public final class NameSplit {

    private NameSplit() {
    }

    /** First names and last names (possibly empty) of the full name. */
    public static String[] of(String fullName) {
        String[] words = fullName == null || fullName.isBlank() ? new String[0] : fullName.strip().split("\\s+");
        int firstNameWords = switch (words.length) {
            case 0 -> 0;
            case 1, 2, 3 -> 1;
            default -> 2;
        };
        return new String[] {
            String.join(" ", Arrays.copyOfRange(words, 0, firstNameWords)),
            String.join(" ", Arrays.copyOfRange(words, firstNameWords, words.length))
        };
    }

}
