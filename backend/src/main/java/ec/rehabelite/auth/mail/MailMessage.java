package ec.rehabelite.auth.mail;

import java.util.Objects;

/**
 * An account email (MEJ-01, TEC-16), already written: one recipient, the subject and the same content as plain text
 * and HTML. The sender is the configured one ({@code app.mail.from}). It may carry an account link with its raw
 * token, so it is never logged.
 */
public record MailMessage(String to, String subject, String text, String html) {

    public MailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(html, "html");
    }

    /** Never print the recipient nor the content. */
    @Override
    public String toString() {
        return "MailMessage[<redacted>]";
    }

}
