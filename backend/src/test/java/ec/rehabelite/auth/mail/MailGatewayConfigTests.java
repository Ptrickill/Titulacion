package ec.rehabelite.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.shared.config.AppProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * TEC-16: app.mail.transport chooses the adapter of the account emails. SMTP is the default, also with an empty
 * MAIL_TRANSPORT (the .env.example line); {@code api} needs BREVO_API_KEY, or the application does not start.
 */
class MailGatewayConfigTests {

    private static final String KEY = "test-only-brevo-key";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class Context {

        @Bean
        JavaMailSender javaMailSender() {
            return new JavaMailSenderImpl();
        }

    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Context.class, MailGatewayConfig.class)
            .withPropertyValues("app.jwt.secret=test-only-secret-for-mail-transport-0123456789",
                    "app.jwt.expiration-minutes=30", "app.cors.allowed-origins=http://localhost:4200",
                    "app.frontend-url=http://localhost:4200", "app.mail.from=no-reply@rehabelite.test",
                    "app.admin.email=", "app.ai.anthropic-api-key=");

    @Test
    void smtpIsTheDefaultAlsoWithAnEmptyTransport() {
        runner.run(context -> {
            assertThat(context.getBean(MailGateway.class)).isInstanceOf(SmtpMailGateway.class);
            assertThat(context.getBean(AppProperties.class).mail().transport())
                    .isEqualTo(AppProperties.MailTransport.SMTP);
        });
        runner.withPropertyValues("app.mail.transport=")
                .run(context -> assertThat(context.getBean(MailGateway.class)).isInstanceOf(SmtpMailGateway.class));
        runner.withPropertyValues("app.mail.transport=smtp")
                .run(context -> assertThat(context.getBean(MailGateway.class)).isInstanceOf(SmtpMailGateway.class));
    }

    @Test
    void apiUsesBrevoWithTheDefaultsOfTheEndpointAndTheTimeouts() {
        for (String transport : new String[] {"api", "API"}) {
            runner.withPropertyValues("app.mail.transport=" + transport, "app.mail.brevo.api-key=" + KEY)
                    .run(context -> {
                        assertThat(context.getBean(MailGateway.class)).isInstanceOf(BrevoApiMailGateway.class);
                        AppProperties.Brevo brevo = context.getBean(AppProperties.class).mail().brevo();
                        assertThat(brevo.baseUrl()).isEqualTo("https://api.brevo.com");
                        assertThat(brevo.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
                        assertThat(brevo.readTimeout()).isEqualTo(Duration.ofSeconds(10));
                    });
        }
    }

    @Test
    void apiWithoutAKeyOrAnUnknownTransportStopsTheApplication() {
        runner.withPropertyValues("app.mail.transport=api").run(context -> assertThat(context.getStartupFailure())
                .hasStackTraceContaining("MAIL_TRANSPORT=api needs BREVO_API_KEY"));
        runner.withPropertyValues("app.mail.transport=api", "app.mail.brevo.api-key=   ")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("MAIL_TRANSPORT=api needs BREVO_API_KEY"));
        runner.withPropertyValues("app.mail.transport=http").run(context -> assertThat(context.getStartupFailure())
                .hasStackTraceContaining("app.mail.transport"));
    }

    @Test
    void theKeyIsNeverPrinted() {
        runner.withPropertyValues("app.mail.transport=api", "app.mail.brevo.api-key=" + KEY)
                .run(context -> assertThat(context.getBean(AppProperties.class).mail().toString())
                        .contains("apiKey=<set>").doesNotContain(KEY));
    }

}
