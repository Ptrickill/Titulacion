package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.AbstractIntegrationTest;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The test profile sets app.admin, so the initial administrator is created when the context starts.
 */
class AdminAccountInitializerIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private AdminAccountInitializer initializer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppProperties properties;

    @Test
    void createsTheConfiguredAdministratorWithABcryptHash() {
        User admin = userRepository.findByEmail(User.normalizeEmail(properties.admin().email())).orElseThrow();

        assertThat(admin.getRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getFullName()).isEqualTo(AdminAccountInitializer.ADMIN_FULL_NAME);
        assertThat(admin.getPasswordHash()).startsWith("$2").isNotEqualTo(properties.admin().password());
        assertThat(passwordEncoder.matches(properties.admin().password(), admin.getPasswordHash())).isTrue();
    }

    @Test
    void doesNothingWhenAnAdministratorAlreadyExists() {
        long before = userRepository.count();

        initializer.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(before);
    }

}
