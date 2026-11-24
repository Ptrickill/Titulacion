package ec.rehabelite.auth.mail;

import org.springframework.mail.MailException;

/**
 * Port through which the account emails leave (TEC-16). {@code AccountMailer} writes the message; the adapter chosen
 * by {@code app.mail.transport} delivers it: {@link SmtpMailGateway} (SMTP, the default) or
 * {@link BrevoApiMailGateway} (Brevo's HTTP API, for production).
 */
public interface MailGateway {

    /** Display name of the sender of every account email. */
    String SENDER_NAME = "Rehab Elite";

    /**
     * Delivers the message, or throws Spring's {@link MailException} (usually {@code MailSendException}) when it
     * could not: callers turn it into a 503 or, for best-effort notices, only log it. Implementations never log the
     * recipient nor the content.
     */
    void send(MailMessage message);

}
