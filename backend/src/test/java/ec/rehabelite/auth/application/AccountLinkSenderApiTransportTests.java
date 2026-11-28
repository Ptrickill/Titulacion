package ec.rehabelite.auth.application;

import ec.rehabelite.TestClinicalText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.AccountTokenPurpose;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.mail.BrevoApiMailGateway;
import ec.rehabelite.shared.config.AppProperties;
import ec.rehabelite.shared.domain.ExternalServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.mail.MailSendException;

/**
 * TEC-16: with the HTTP transport, a link that Brevo does not accept fails exactly like one that the SMTP server does
 * not accept: the same {@link ExternalServiceException} (503) with the same Spanish message, so the caller's
 * transaction rolls back. A local JDK server plays Brevo.
 */
class AccountLinkSenderApiTransportTests {

    private static final String RECIPIENT = "invitado.api@rehabelite.test";

    private final AtomicInteger status = new AtomicInteger(201);
    private final AtomicReference<String> body = new AtomicReference<>();
    private HttpServer server;
    private AccountLinkSender sender;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                body.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        String baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();

        AppProperties.Mail mail = new AppProperties.Mail("no-reply@rehabelite.test", AppProperties.MailTransport.API,
                new AppProperties.Brevo("test-only-brevo-key", baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(2)));
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("test-only-secret-for-api-transport-0123456789", 30),
                new AppProperties.InitialDirector(null, null), new AppProperties.Cors(List.of("http://localhost:4200")),
                new AppProperties.Ai(null), null, "http://localhost:4200", mail,
                new AppProperties.Tokens(Duration.ofHours(48), Duration.ofHours(1)),
                new AppProperties.Clinic(LocalTime.of(7, 0), LocalTime.of(20, 0)),
                new AppProperties.ClinicalData(TestClinicalText.KEY, ""),
                new AppProperties.Session(Duration.ofMinutes(15), Duration.ofMinutes(2)),
                new AppProperties.Compliance(15));
        BrevoApiMailGateway gateway = new BrevoApiMailGateway(baseUrl, mail.brevo().apiKey(), mail.from(),
                mail.brevo().connectTimeout(), mail.brevo().readTimeout());

        AccountTokenService tokenService = mock(AccountTokenService.class);
        when(tokenService.issue(any(), any(AccountTokenPurpose.class), any(Instant.class)))
                .thenReturn("A".repeat(43));
        sender = new AccountLinkSender(tokenService, new AccountMailer(new MailDispatcher(gateway, userId -> false), properties), new SyncTaskExecutor());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void anAcceptedActivationLeavesThroughTheApi() {
        sender.sendActivation(invitedUser(), Instant.now());

        assertThat(body.get()).contains(RECIPIENT, AccountMailer.ACTIVATION_SUBJECT,
                "http://localhost:4200/activar-cuenta?token=" + "A".repeat(43));
    }

    @Test
    void aRejectedOrFailedActivationIsTheSame503AsWithSmtp() {
        for (int failure : new int[] {400, 401, 500, 503}) {
            status.set(failure);
            assertThatThrownBy(() -> sender.sendActivation(invitedUser(), Instant.now()))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessage(AccountLinkSender.ACTIVATION_MAIL_FAILED)
                    .hasCauseInstanceOf(MailSendException.class);
        }
    }

    private static User invitedUser() {
        return User.create(RECIPIENT, "not-a-real-hash", "Invitado Api", Role.PHYSIOTHERAPIST, null, Instant.now());
    }

}
