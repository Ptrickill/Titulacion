package ec.rehabelite.shared.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Capitalization of names (MEJ-03): extra spaces removed and each word with a capital initial and the rest in lower
 * case ("MARÍA  josé" → "María José"). The particles de, del, la, las, los and y stay in lower case unless they
 * start the name ("SAN MIGUEL DE LOS BANCOS" → "San Miguel de los Bancos"). A letter after "(" or "-" is also a
 * word start ("(CAB. EN IRQUIS)" → "(Cab. En Irquis)").
 * <p>
 * Used for the names and surnames of patients, which are stored this way, and to show the official INEC place
 * names, which are stored as published. The frontend applies the same rule ({@code shared/text/name-case.ts}).
 */
public final class NameCase {

    private static final Locale SPANISH = Locale.forLanguageTag("es-EC");
    private static final Set<String> PARTICLES = Set.of("de", "del", "la", "las", "los", "y");

    private NameCase() {
    }

    public static String of(String text) {
        if (text == null) {
            return null;
        }
        String collapsed = text.strip().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) {
            return collapsed;
        }
        String[] words = collapsed.toLowerCase(SPANISH).split(" ");
        StringBuilder result = new StringBuilder(collapsed.length());
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(' ');
            }
            result.append(i > 0 && PARTICLES.contains(words[i]) ? words[i] : capitalize(words[i]));
        }
        return result.toString();
    }

    private static String capitalize(String word) {
        StringBuilder result = new StringBuilder(word.length());
        boolean start = true;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            result.append(start && Character.isLetter(c) ? Character.toUpperCase(c) : c);
            if (Character.isLetter(c)) {
                start = false;
            } else if (c == '(' || c == '-') {
                start = true;
            }
        }
        return result.toString();
    }

}
