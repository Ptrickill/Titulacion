package ec.rehabelite.auth.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * SMTP adapter (MEJ-01, the default transport): a multipart MIME message with the plain text and the HTML, sent with
 * Spring Boot's {@link JavaMailSender} (the spring.mail server; Mailpit in development, the recording sender in the
 * integration tests). A delivery failure is Spring's {@code MailException}.
 */
public class SmtpMailGateway implements MailGateway {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpMailGateway(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(MailMessage message) {
        MimeMessage mime = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from, SENDER_NAME);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new MailPreparationException("Could not prepare the account email", ex);
        }
        mailSender.send(mime);
    }

}
