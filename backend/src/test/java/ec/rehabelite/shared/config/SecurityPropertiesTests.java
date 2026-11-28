package ec.rehabelite.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * MEJ-06: without a valid CLINICAL_DATA_KEY (32 bytes in Base64) the application does not start, never prints the key,
 * and the session and compliance settings have their defaults.
 */
class SecurityPropertiesTests {

    private static final String KEY = "AMRp+cyxpXVxQnCIXks0M2MdvAYMHYNvP5Rl4YUbhYM=";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class Properties {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Properties.class)
            .withPropertyValues("app.jwt.secret=test-only-secret-for-security-properties-0123456789",
                    "app.jwt.expiration-minutes=30", "app.cors.allowed-origins=http://localhost:4200",
                    "app.frontend-url=http://localhost:4200", "app.mail.from=no-reply@rehabelite.test",
                    "app.initial-director.email=", "app.ai.anthropic-api-key=");

    @Test
    void startsWithAValidKeyAndTheDefaultsOfTheSessionAndTheRequests() {
        runner.withPropertyValues("app.clinical-data.key=" + KEY).run(context -> {
            assertThat(context).hasNotFailed();
            AppProperties properties = context.getBean(AppProperties.class);
            assertThat(properties.session().idleTimeout()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.session().idleWarning()).isEqualTo(Duration.ofMinutes(2));
            assertThat(properties.compliance().requestResponseDays()).isEqualTo(15);
            assertThat(properties.clinicalData().previousKeyList()).isEmpty();
            assertThat(properties.clinicalData().toString()).doesNotContain(KEY).contains("<set>");
        });
    }

    @Test
    void withoutTheKeyOrWithAnInvalidOneTheApplicationDoesNotStart() {
        runner.run(context -> assertThat(context).hasFailed());
        for (String key : new String[] {"", "no-es-base64!", "c2hvcnQ=", KEY + "AAAA"}) {
            runner.withPropertyValues("app.clinical-data.key=" + key)
                    .run(context -> assertThat(context).hasFailed());
        }
        runner.withPropertyValues("app.clinical-data.key=" + KEY, "app.clinical-data.previous-keys=" + KEY + ",corta")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void theWarningMustComeBeforeTheTimeout() {
        runner.withPropertyValues("app.clinical-data.key=" + KEY, "app.session.idle-timeout=5m",
                "app.session.idle-warning=5m").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.clinical-data.key=" + KEY, "app.session.idle-timeout=30m",
                "app.session.idle-warning=1m").run(context -> assertThat(context).hasNotFailed());
    }

}
