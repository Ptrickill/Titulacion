package ec.rehabelite.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * TEC-06: the synthetic seed data belongs to the dev profile only. Without a profile (and with "test" or
 * "prod") Flyway only reads db/migration, and dev is never active by default.
 */
class ProfileConfigurationTests {

    @Test
    void devIsNotTheDefaultProfileAndTheCommonFlywayLocationsHaveNoSeed() throws IOException {
        PropertySource<?> common = load("application.yml");

        assertThat(common.getProperty("spring.profiles.default")).isNull();
        assertThat(common.getProperty("spring.profiles.active")).isNull();
        assertThat(common.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(List.of(common.getProperty("spring.flyway.ignore-migration-patterns[0]"),
                common.getProperty("spring.flyway.ignore-migration-patterns[1]")))
                .containsExactly("*:future", "repeatable:missing");
    }

    @Test
    void onlyTheDevProfileAddsTheSeedLocation() throws IOException {
        assertThat(load("application-dev.yml").getProperty("spring.flyway.locations"))
                .isEqualTo("classpath:db/migration,classpath:db/dev-seed");
        assertThat(load("application-test.yml").getProperty("spring.flyway.locations")).isNull();
    }

    private static PropertySource<?> load(String file) throws IOException {
        return new YamlPropertySourceLoader().load(file, new ClassPathResource(file)).getFirst();
    }

}
