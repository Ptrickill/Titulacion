package ec.rehabelite.auth.application;

import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.config.AppProperties;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Dev profile only (TEC-06): gives the synthetic seed accounts (db/dev-seed/R__dev_seed.sql) the password
 * DEV_SEED_PASSWORD from .env, stored as a BCrypt hash. The seed script creates them with a marker that is not a
 * hash, so they cannot log in until this runs; neither the password nor its hash is ever in the repository.
 * Accounts whose hash already matches are left untouched, so changing DEV_SEED_PASSWORD only takes a restart.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
class DevSeedPasswordInitializer implements ApplicationRunner {

    /** Every account of the seed script, and only those, uses this email domain. */
    static final String SEED_EMAIL_DOMAIN = "@dev-seed.rehabelite.test";

    private static final Logger log = LoggerFactory.getLogger(DevSeedPasswordInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> seedAccounts = userRepository.findByEmailEndingWith(SEED_EMAIL_DOMAIN);
        if (seedAccounts.isEmpty()) {
            return;
        }
        String password = properties.devSeed() == null ? null : properties.devSeed().password();
        if (!StringUtils.hasText(password)) {
            log.warn("DEV_SEED_PASSWORD is not set; the {} development seed accounts cannot log in",
                    seedAccounts.size());
            return;
        }
        int updated = 0;
        for (User account : seedAccounts) {
            if (!isBcryptOf(password, account.getPasswordHash())) {
                account.changePasswordHash(passwordEncoder.encode(password), null, clock.instant());
                updated++;
            }
        }
        log.info("Development seed accounts ready: total={} passwordsUpdated={}", seedAccounts.size(), updated);
    }

    /** The seed marker is not BCrypt; checking it would only log a warning from the encoder. */
    private boolean isBcryptOf(String password, String hash) {
        return hash.startsWith("$2") && passwordEncoder.matches(password, hash);
    }

}
