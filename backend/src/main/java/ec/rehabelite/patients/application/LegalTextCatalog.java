package ec.rehabelite.patients.application;

import ec.rehabelite.patients.ConsentType;
import ec.rehabelite.patients.LegalText;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The legal texts in force (MEJ-06), versioned in the repository under {@code resources/legal}: the privacy notice and
 * the text of each consent. Each file starts with a header between {@code ---} lines with its {@code version} and
 * {@code title}, followed by its Markdown content. They are read once at startup, and a missing or malformed file
 * stops the application. Publishing a new version means editing the file and its version: the consents given for an
 * earlier version stay valid and show as "Versión anterior".
 */
@Component
public class LegalTextCatalog {

    static final String PRIVACY_NOTICE = "privacy-notice";
    private static final Map<ConsentType, String> CONSENT_FILES = Map.of(
            ConsentType.DATA_PROCESSING, "consent-data-processing",
            ConsentType.EMAIL_COMMUNICATIONS, "consent-email-communications");

    private final LegalText privacyNotice;
    private final Map<ConsentType, LegalText> consents = new EnumMap<>(ConsentType.class);

    public LegalTextCatalog() {
        privacyNotice = read(PRIVACY_NOTICE);
        for (ConsentType type : ConsentType.values()) {
            consents.put(type, read(CONSENT_FILES.get(type)));
        }
    }

    public LegalText privacyNotice() {
        return privacyNotice;
    }

    public LegalText consent(ConsentType type) {
        return consents.get(type);
    }

    /** The consent texts in the order of {@link ConsentType}. */
    public List<LegalText> consents() {
        return List.copyOf(consents.values());
    }

    /** The version in force of a consent text. */
    public String currentVersion(ConsentType type) {
        return consents.get(type).version();
    }

    static LegalText parse(String key, String source) {
        String text = source.replace("\r\n", "\n");
        if (!text.startsWith("---\n")) {
            throw new IllegalStateException("Legal text without header: " + key);
        }
        int end = text.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalStateException("Legal text with an unterminated header: " + key);
        }
        String version = null;
        String title = null;
        for (String line : text.substring(4, end).split("\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            if (name.equals("version")) {
                version = value;
            } else if (name.equals("title")) {
                title = value;
            }
        }
        if (version == null || version.isEmpty() || version.length() > 30 || title == null || title.isEmpty()) {
            throw new IllegalStateException("Legal text without a version (at most 30 characters) or a title: " + key);
        }
        return new LegalText(key, version, title, text.substring(end + 5).strip());
    }

    private static LegalText read(String key) {
        try (InputStream in = new ClassPathResource("legal/" + key + ".md").getInputStream()) {
            return parse(key, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Missing legal text " + key, e);
        }
    }

}
