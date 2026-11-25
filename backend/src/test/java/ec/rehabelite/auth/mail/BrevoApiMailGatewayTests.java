package ec.rehabelite.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TEC-16: the Brevo adapter against a local HTTP server of the JDK (it never calls Brevo). A 2xx answer delivers the
 * message; a 4xx, a 5xx, a timeout or an unreachable host is a {@link MailSendException}, the failure that callers
 * already turn into a 503. Neither the recipient nor the content reach the exception or the logs.
 */
@ExtendWith(OutputCaptureExtension.class)
class BrevoApiMailGatewayTests {

    private static final String API_KEY = "test-only-brevo-key";
    private static final String FROM = "no-reply@rehabelite.test";
    private static final String RECIPIENT = "destinatario.brevo@rehabelite.test";
    private static final MailMessage MESSAGE = new MailMessage(RECIPIENT, "Restablezca su contraseña de Rehab Elite",
            "Hola, María José:\n\nhttp://localhost:4200/restablecer-contrasena?token=abc",
            "<p>Hola, María José:</p><a href=\"http://localhost:4200/restablecer-contrasena?token=abc\">Restablecer</a>");

    private final JsonMapper json = JsonMapper.builder().build();
    private final ExecutorService serverThreads = Executors.newCachedThreadPool();
    private final AtomicReference<Exchange> received = new AtomicReference<>();
    private HttpServer server;
    private volatile int status = 201;
    private volatile String answer = "{\"messageId\":\"<202611241200.123@smtp-relay.mailin.fr>\"}";
    private volatile Duration delay = Duration.ZERO;

    private record Exchange(String method, String path, String apiKey, String contentType, String body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(serverThreads);
        server.createContext("/", exchange -> {
            try (InputStream body = exchange.getRequestBody()) {
                received.set(new Exchange(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("api-key"),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        new String(body.readAllBytes(), StandardCharsets.UTF_8)));
            }
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        serverThreads.shutdownNow();
    }

    @Test
    void postsTheMessageToTheTransactionalEmailEndpointWithTheApiKey(CapturedOutput output) {
        gateway(Duration.ofSeconds(10)).send(MESSAGE);

        Exchange exchange = received.get();
        assertThat(exchange.method()).isEqualTo("POST");
        assertThat(exchange.path()).isEqualTo("/v3/smtp/email");
        assertThat(exchange.apiKey()).isEqualTo(API_KEY);
        assertThat(exchange.contentType()).startsWith("application/json");
        JsonNode body = json.readTree(exchange.body());
        assertThat(body.get("sender").get("name").asString()).isEqualTo("Rehab Elite");
        assertThat(body.get("sender").get("email").asString()).isEqualTo(FROM);
        assertThat(body.get("to")).hasSize(1);
        assertThat(body.get("to").get(0).get("email").asString()).isEqualTo(RECIPIENT);
        assertThat(body.get("subject").asString()).isEqualTo(MESSAGE.subject());
        assertThat(body.get("textContent").asString()).isEqualTo(MESSAGE.text());
        assertThat(body.get("htmlContent").asString()).isEqualTo(MESSAGE.html());
        assertThat(output).doesNotContain(RECIPIENT, "token=abc", API_KEY);
    }

    @Test
    void aRejectedRequestIsAMailSendExceptionWithoutTheRecipient(CapturedOutput output) {
        status = 400;
        answer = "{\"code\":\"invalid_parameter\",\"message\":\"email is not valid: " + RECIPIENT + "\"}";

        assertThatThrownBy(() -> gateway(Duration.ofSeconds(10)).send(MESSAGE))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("400")
                .hasMessageNotContaining(RECIPIENT)
                .hasNoCause();
        assertThat(output).contains("status=400").doesNotContain(RECIPIENT, "token=abc", API_KEY);
    }

    @Test
    void anUnauthorizedKeyIsAMailSendException() {
        status = 401;
        answer = "{\"code\":\"unauthorized\",\"message\":\"Key not found\"}";

        assertThatThrownBy(() -> gateway(Duration.ofSeconds(10)).send(MESSAGE))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("401");
    }

    @Test
    void aServerErrorIsAMailSendException(CapturedOutput output) {
        status = 503;
        answer = "{\"message\":\"Service unavailable\"}";

        assertThatThrownBy(() -> gateway(Duration.ofSeconds(10)).send(MESSAGE))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("503")
                .hasNoCause();
        assertThat(output).contains("status=503").doesNotContain(RECIPIENT);
    }

    @Test
    void anAnswerSlowerThanTheReadTimeoutIsAMailSendException(CapturedOutput output) {
        delay = Duration.ofSeconds(5);
        long start = System.nanoTime();

        assertThatThrownBy(() -> gateway(Duration.ofMillis(300)).send(MESSAGE))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("not reachable")
                .hasMessageNotContaining(RECIPIENT)
                .hasNoCause();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
        assertThat(output).contains("Brevo API not reachable").doesNotContain(RECIPIENT, "token=abc");
    }

    @Test
    void anUnreachableHostIsAMailSendException() {
        String baseUrl = baseUrl();
        server.stop(0);

        BrevoApiMailGateway gateway = new BrevoApiMailGateway(baseUrl, API_KEY, FROM, Duration.ofSeconds(2),
                Duration.ofSeconds(2));
        assertThatThrownBy(() -> gateway.send(MESSAGE))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("not reachable");
    }

    @Test
    void theMessageNeverPrintsItsRecipientNorItsContent() {
        assertThat(MESSAGE.toString()).doesNotContain(RECIPIENT, "token", "contraseña");
    }

    private BrevoApiMailGateway gateway(Duration readTimeout) {
        return new BrevoApiMailGateway(baseUrl(), API_KEY, FROM, Duration.ofSeconds(2), readTimeout);
    }

    private String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

}
