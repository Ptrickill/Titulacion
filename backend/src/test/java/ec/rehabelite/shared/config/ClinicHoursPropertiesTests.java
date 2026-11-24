package ec.rehabelite.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * MEJ-04: app.clinic.* binds HH:mm values and may only narrow the hours that V8 allows (07:00 to 20:00); otherwise the
 * application does not start.
 */
class ClinicHoursPropertiesTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class Properties {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Properties.class)
            .withPropertyValues("app.jwt.secret=test-only-secret-for-clinic-hours-0123456789",
                    "app.jwt.expiration-minutes=30", "app.cors.allowed-origins=http://localhost:4200",
                    "app.frontend-url=http://localhost:4200", "app.mail.from=no-reply@rehabelite.test",
                    "app.admin.email=", "app.ai.anthropic-api-key=");

    @Test
    void bindsTheDefaultsAndNarrowerHours() {
        runner.run(context -> assertThat(context.getBean(AppProperties.class).clinic())
                .isEqualTo(new AppProperties.Clinic(LocalTime.of(7, 0), LocalTime.of(20, 0))));
        runner.withPropertyValues("app.clinic.opening-time=08:30", "app.clinic.closing-time=18:00")
                .run(context -> assertThat(context.getBean(AppProperties.class).clinic())
                        .isEqualTo(new AppProperties.Clinic(LocalTime.of(8, 30), LocalTime.of(18, 0))));
    }

    @Test
    void wideningOrInvertingTheHoursStopsTheApplication() {
        for (String[] hours : new String[][] {{"06:00", "20:00"}, {"07:00", "21:00"}, {"18:00", "08:00"}}) {
            runner.withPropertyValues("app.clinic.opening-time=" + hours[0], "app.clinic.closing-time=" + hours[1])
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void theBoundsAreTheOnesOfTheDatabase() {
        assertThat(validator.validate(new AppProperties.Clinic(LocalTime.of(7, 0), LocalTime.of(20, 0)))).isEmpty();
        assertThat(validator.validate(new AppProperties.Clinic(LocalTime.of(6, 59), LocalTime.of(20, 0))))
                .singleElement().satisfies(violation -> assertThat(violation.getMessage())
                        .isEqualTo("app.clinic.opening-time must be before closing-time, both within 07:00 and 20:00"));
    }

}
