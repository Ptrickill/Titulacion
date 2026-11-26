package ec.rehabelite.auth.application;

import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.StaffAccountCreated;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.config.AppProperties;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the initial operations director (MEJ-05) from app.initial-director (INITIAL_DIRECTOR_EMAIL /
 * INITIAL_DIRECTOR_PASSWORD) at startup when no active operations director exists: the director manages the staff,
 * including the reception, so the system always needs one. The account is created already activated, with its
 * password. Existing accounts are never modified.
 * <p>
 * It is the only account not created from a registry: it publishes {@link StaffAccountCreated} in the same
 * transaction, so the staff module creates its record (with data to complete). When the variables are missing or the
 * email belongs to another account, it only logs an error: the application starts, but nobody can manage the staff
 * until a director exists.
 */
@Component
@RequiredArgsConstructor
class DirectorAccountInitializer implements ApplicationRunner {

    static final String DIRECTOR_FULL_NAME = "Dirección Operativa";

    private static final Logger log = LoggerFactory.getLogger(DirectorAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsActiveWithRole(Role.OPERATIONS_DIRECTOR)) {
            return;
        }
        AppProperties.InitialDirector director = properties.initialDirector();
        if (!StringUtils.hasText(director.email()) || !StringUtils.hasText(director.password())) {
            log.error("No active operations director exists and INITIAL_DIRECTOR_EMAIL / INITIAL_DIRECTOR_PASSWORD "
                    + "are not set: nobody can manage the staff");
            return;
        }
        String email = User.normalizeEmail(director.email());
        if (!userRepository.findByEmailOrPendingEmail(email, email).isEmpty()) {
            log.error("No active operations director exists and INITIAL_DIRECTOR_EMAIL belongs to another account: "
                    + "set an unused email");
            return;
        }
        User user = userRepository.save(User.create(email, passwordEncoder.encode(director.password()),
                DIRECTOR_FULL_NAME, Role.OPERATIONS_DIRECTOR, null, clock.instant()));
        events.publishEvent(new StaffAccountCreated(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole()));
        log.info("Initial operations director account created userId={}", user.getId());
    }

}
