package ec.rehabelite;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Mail sender of the integration tests (MEJ-01): builds the real MIME messages but keeps them in memory instead of
 * talking to an SMTP server. Tests use unique email addresses, so they read only their own messages. A recipient
 * marked with {@link #failFor(String)} makes the send fail, like an unreachable SMTP server.
 * <p>
 * {@link AbstractIntegrationTest} registers it with {@code @Import}, so Spring Boot's own mail sender backs off. It
 * has no stereotype annotation on purpose: the throwaway backend ({@code spring-boot:test-run}, which scans the test
 * classes too) must keep sending real emails to Mailpit.
 */
public class RecordingMailSender extends JavaMailSenderImpl {

    /** Token of the emailed links: 43 URL-safe Base64 characters. */
    private static final Pattern LINK_TOKEN = Pattern.compile("token=([A-Za-z0-9_-]{43})");

    private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
    private final Set<String> failing = ConcurrentHashMap.newKeySet();

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
        for (MimeMessage message : mimeMessages) {
            if (recipients(message).stream().anyMatch(failing::contains)) {
                throw new MailSendException("Simulated SMTP failure");
            }
        }
        for (MimeMessage message : mimeMessages) {
            try {
                // What the SMTP transport does before sending: the headers then match the content
                message.saveChanges();
            } catch (MessagingException ex) {
                throw new MailSendException("Invalid message", ex);
            }
        }
        sent.addAll(Arrays.asList(mimeMessages));
    }

    public void failFor(String email) {
        failing.add(email);
    }

    public void stopFailingFor(String email) {
        failing.remove(email);
    }

    public List<MimeMessage> messagesTo(String email) {
        return sent.stream().filter(m -> recipients(m).contains(email)).toList();
    }

    /** For emails sent on another thread (password reset): waits up to 5 s for that many messages. */
    public List<MimeMessage> awaitMessagesTo(String email, int count) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        List<MimeMessage> messages = messagesTo(email);
        while (messages.size() < count && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            messages = messagesTo(email);
        }
        return messages;
    }

    /** Raw token of the link in the plain text part of the message. */
    public static String tokenOf(MimeMessage message) {
        Matcher matcher = LINK_TOKEN.matcher(plainText(message));
        if (!matcher.find()) {
            throw new AssertionError("The message has no account link");
        }
        return matcher.group(1);
    }

    public static String subjectOf(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (MessagingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String plainText(MimeMessage message) {
        return textOf(message, "text/plain");
    }

    public static String html(MimeMessage message) {
        return textOf(message, "text/html");
    }

    private static String textOf(Part part, String mimeType) {
        try {
            Object content = part.getContent();
            if (content instanceof String text) {
                return part.isMimeType(mimeType) ? text : null;
            }
            if (content instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    String text = textOf(multipart.getBodyPart(i), mimeType);
                    if (text != null) {
                        return text;
                    }
                }
            }
            return null;
        } catch (MessagingException | IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static List<String> recipients(MimeMessage message) {
        try {
            Address[] addresses = message.getAllRecipients();
            List<String> emails = new ArrayList<>();
            if (addresses != null) {
                for (Address address : addresses) {
                    emails.add(address.toString());
                }
            }
            return emails;
        } catch (MessagingException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
