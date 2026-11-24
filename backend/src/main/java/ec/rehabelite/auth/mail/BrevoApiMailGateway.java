package ec.rehabelite.auth.mail;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.MailSendException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP adapter (TEC-16) for production, where outbound SMTP is not available: {@code POST /v3/smtp/email} of Brevo's
 * transactional email API with the {@code api-key} header. A 4xx or 5xx answer, a timeout (5 s to connect and 10 s to
 * read by default) or an unreachable host throws {@link MailSendException}, the same failure as an SMTP server that
 * does not answer, so callers keep their 503 or their best-effort log.
 * <p>
 * Nothing about the message reaches the logs or the exception: neither the recipient, the subject, the content nor
 * Brevo's answer body (it may repeat the address). Only the HTTP status or the type of the network failure.
 */
public class BrevoApiMailGateway implements MailGateway {

    static final String SEND_PATH = "/v3/smtp/email";
    static final String API_KEY_HEADER = "api-key";

    private static final Logger log = LoggerFactory.getLogger(BrevoApiMailGateway.class);

    private final RestClient client;
    private final String from;

    public BrevoApiMailGateway(String baseUrl, String apiKey, String from, Duration connectTimeout,
            Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader(API_KEY_HEADER, apiKey)
                .build();
        this.from = from;
    }

    @Override
    public void send(MailMessage message) {
        SendRequest request = new SendRequest(new Contact(SENDER_NAME, from), List.of(new Recipient(message.to())),
                message.subject(), message.html(), message.text());
        try {
            client.post()
                    .uri(SEND_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("Brevo API rejected the account email status={}", status);
            throw new MailSendException("Brevo API rejected the email with status " + status);
        } catch (RestClientException ex) {
            String cause = ex.getMostSpecificCause().getClass().getName();
            log.warn("Brevo API not reachable cause={}", cause);
            throw new MailSendException("Brevo API not reachable: " + cause);
        }
    }

    /** Body of POST /v3/smtp/email: sender, recipients, subject and both versions of the content. */
    record SendRequest(Contact sender, List<Recipient> to, String subject, String htmlContent, String textContent) {
    }

    record Contact(String name, String email) {
    }

    record Recipient(String email) {
    }

}
