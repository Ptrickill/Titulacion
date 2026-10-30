package ec.rehabelite.auth.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login use case: checks the credentials, applies the lockout policy and issues the access token.
 */
@Service
public class AuthService {

    static final String AUDITED_ENTITY = "User";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /** Compared when the email is unknown, so the response time does not reveal whether an account exists. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService tokenService,
            AuditLogService auditLogService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Failed attempts are committed even though the method ends with an exception.
     *
     * @throws InvalidCredentialsException for an unknown email, a wrong password, or a locked or inactive account
     */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResult login(String email, String password) {
        Instant now = clock.instant();
        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(email));
        boolean passwordMatches = passwordMatches(password, found.map(User::getPasswordHash).orElse(dummyPasswordHash));

        if (found.isEmpty()) {
            log.info("Login rejected: unknown account");
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        if (!user.isActive()) {
            log.info("Login rejected: inactive account userId={}", user.getId());
            throw new InvalidCredentialsException();
        }
        if (user.isLocked(now)) {
            log.info("Login rejected: locked account userId={}", user.getId());
            throw new InvalidCredentialsException();
        }
        if (!passwordMatches) {
            user.registerFailedAttempt(now);
            if (user.isLocked(now)) {
                log.warn("Account locked after {} failed attempts userId={}", User.MAX_FAILED_ATTEMPTS, user.getId());
            } else {
                log.info("Login rejected: wrong password userId={} failedAttempts={}", user.getId(),
                        user.getFailedAttempts());
            }
            throw new InvalidCredentialsException();
        }

        user.registerSuccessfulLogin(now);
        auditLogService.recordFor(user.getId(), AuditAction.LOGIN, AUDITED_ENTITY, user.getId());
        log.info("Login succeeded userId={}", user.getId());
        return tokenService.issue(user, now);
    }

    private boolean passwordMatches(String rawPassword, String passwordHash) {
        try {
            return passwordEncoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException ex) {
            // BCrypt rejects passwords longer than 72 bytes: such a password can never be valid
            return false;
        }
    }

}
