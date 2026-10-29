package ec.rehabelite.auth.application;

import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.config.AppProperties;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the initial administrator from app.admin (ADMIN_EMAIL / ADMIN_PASSWORD) at startup when no
 * ADMINISTRATOR account exists yet. Existing accounts are never modified.
 */
@Component
@RequiredArgsConstructor
class AdminAccountInitializer implements ApplicationRunner {

    static final String ADMIN_FULL_NAME = "Administrador del sistema";

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMINISTRATOR)) {
            return;
        }
        AppProperties.Admin admin = properties.admin();
        if (!StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
            log.warn("No administrator account exists and app.admin email/password are not set; skipping creation");
            return;
        }
        String email = User.normalizeEmail(admin.email());
        if (userRepository.existsByEmail(email)) {
            log.warn("Cannot create the initial administrator: the configured email belongs to another account");
            return;
        }
        User user = userRepository.save(User.create(email, passwordEncoder.encode(admin.password()),
                ADMIN_FULL_NAME, Role.ADMINISTRATOR, null, clock.instant()));
        log.info("Initial administrator account created userId={}", user.getId());
    }

}
