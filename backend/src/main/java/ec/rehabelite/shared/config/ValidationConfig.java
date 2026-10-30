package ec.rehabelite.shared.config;

import ec.rehabelite.shared.domain.ClinicCalendar;
import java.time.Clock;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean Validation date constraints (@Past, @PastOrPresent, ...) use the application {@link Clock} in the clinic
 * time zone, so "today" is the date in Quito and tests can fix it.
 */
@Configuration(proxyBeanMethods = false)
class ValidationConfig {

    @Bean
    ValidationConfigurationCustomizer clinicClockProvider(Clock clock) {
        Clock clinicClock = clock.withZone(ClinicCalendar.ZONE);
        return configuration -> configuration.clockProvider(() -> clinicClock);
    }

}
