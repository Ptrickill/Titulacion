package ec.rehabelite.shared.config;

import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

/**
 * User-facing messages (including Bean Validation messages) are always in Spanish (Ecuador),
 * regardless of the client's Accept-Language header.
 */
@Configuration(proxyBeanMethods = false)
class LocaleConfig {

    static final Locale SPANISH_ECUADOR = Locale.forLanguageTag("es-EC");

    @Bean
    LocaleResolver localeResolver() {
        return new FixedLocaleResolver(SPANISH_ECUADOR);
    }

}
