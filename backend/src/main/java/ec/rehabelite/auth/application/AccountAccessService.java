package ec.rehabelite.auth.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.domain.AccountTokenPurpose;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.domain.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Public account links (MEJ-01): activating an account, asking for a password reset and resetting the password.
 * None of them needs a session. The raw tokens and the passwords are never logged nor audited.
 */
@Service
public class AccountAccessService {

    static final String AUDITED_ENTITY = "User";
    static final String PASSWORD_MISMATCH = "La confirmación no coincide con la contraseña.";
    static final String PASSWORD_TOO_LONG = "La contraseña no puede superar los 72 bytes.";
    /** Same answer whether or not the email belongs to an account, so the endpoint does not reveal accounts. */
    public static final String RESET_REQUESTED = "Si el correo corresponde a una cuenta activa, recibirá en unos "
            + "minutos un enlace para restablecer la contraseña.";

    /** Reset links an account can receive per {@link #RESET_WINDOW}. */
    static final int MAX_RESET_REQUESTS = 3;
    static final Duration RESET_WINDOW = Duration.ofHours(1);

    /** BCrypt ignores (Spring Security rejects) anything beyond 72 bytes. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private static final Logger log = LoggerFactory.getLogger(AccountAccessService.class);

    private final UserRepository userRepository;
    private final AccountTokenService tokenService;
    private final AccountMailer mailer;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final TaskExecutor mailExecutor;
    private final Clock clock;

    public AccountAccessService(UserRepository userRepository, AccountTokenService tokenService, AccountMailer mailer,
            PasswordEncoder passwordEncoder, AuditLogService auditLogService,
            @Qualifier("applicationTaskExecutor") TaskExecutor mailExecutor, Clock clock) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.mailer = mailer;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.mailExecutor = mailExecutor;
        this.clock = clock;
    }

    /**
     * Sets the first password of an account pending activation and marks its email as verified. Audited as
     * ACTIVATION, attributed to the account itself.
     *
     * @throws InvalidRequestException if the confirmation differs, the password is too long, or the link is
     *         unknown, used, replaced or expired, or belongs to a deactivated or already activated account
     */
    @Transactional
    public void activate(String token, String password, String passwordConfirmation) {
        checkPassword(password, passwordConfirmation);
        Instant now = clock.instant();
        User user = userOf(tokenService.consume(token, AccountTokenPurpose.ACTIVATION, now));
        if (!user.isActive() || !user.isPendingActivation()) {
            throw new InvalidRequestException(AccountTokenService.INVALID_LINK);
        }
        user.completeActivation(passwordEncoder.encode(password), now);
        auditLogService.recordFor(user.getId(), AuditAction.ACTIVATION, AUDITED_ENTITY, user.getId());
        log.info("Account activated userId={}", user.getId());
    }

    /**
     * Emails a reset link when the email belongs to an active, verified account that has received fewer than
     * {@value #MAX_RESET_REQUESTS} links in the last hour. The caller always answers the same, and the email is
     * sent after the commit on another thread, so neither the answer nor its timing reveals whether the account
     * exists. Every request is audited as RESET_REQUEST: with the account id, or without it for an unknown email
     * (only the IP); the email itself is never stored.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(email));
        if (found.isEmpty()) {
            auditLogService.record(AuditAction.RESET_REQUEST, AUDITED_ENTITY, null);
            log.info("Password reset requested for an unknown email");
            return;
        }
        User user = found.get();
        auditLogService.record(AuditAction.RESET_REQUEST, AUDITED_ENTITY, user.getId());
        if (!user.isActive() || user.isPendingActivation()) {
            log.info("Password reset not sent: account not active or pending activation userId={}", user.getId());
            return;
        }
        Instant now = clock.instant();
        long recent = tokenService.countIssuedSince(user.getId(), AccountTokenPurpose.PASSWORD_RESET,
                now.minus(RESET_WINDOW));
        if (recent >= MAX_RESET_REQUESTS) {
            log.warn("Password reset not sent: request limit reached userId={}", user.getId());
            return;
        }
        String token = tokenService.issue(user.getId(), AccountTokenPurpose.PASSWORD_RESET, now);
        sendAfterCommit(user, token);
    }

    /**
     * Sets a new password through a reset link, clears the failed attempts and the lock, and invalidates the access
     * tokens issued before. Audited as PASSWORD_RESET, attributed to the account itself.
     *
     * @throws InvalidRequestException like {@link #activate}, also for a deactivated or pending account
     */
    @Transactional
    public void resetPassword(String token, String password, String passwordConfirmation) {
        checkPassword(password, passwordConfirmation);
        Instant now = clock.instant();
        User user = userOf(tokenService.consume(token, AccountTokenPurpose.PASSWORD_RESET, now));
        if (!user.isActive() || user.isPendingActivation()) {
            throw new InvalidRequestException(AccountTokenService.INVALID_LINK);
        }
        user.resetPassword(passwordEncoder.encode(password), now);
        auditLogService.recordFor(user.getId(), AuditAction.PASSWORD_RESET, AUDITED_ENTITY, user.getId());
        log.info("Password reset userId={}", user.getId());
    }

    private User userOf(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new InvalidRequestException(AccountTokenService.INVALID_LINK));
    }

    private static void checkPassword(String password, String confirmation) {
        if (!password.equals(confirmation)) {
            throw new InvalidRequestException(PASSWORD_MISMATCH);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidRequestException(PASSWORD_TOO_LONG);
        }
    }

    /** The email leaves after the commit, on another thread: a slow SMTP server never delays the answer. */
    private void sendAfterCommit(User user, String token) {
        Long userId = user.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mailExecutor.execute(() -> {
                    try {
                        mailer.sendPasswordReset(user, token);
                        log.info("Password reset link sent userId={}", userId);
                    } catch (MailException ex) {
                        log.warn("Password reset link not sent userId={} cause={}", userId, ex.getClass().getName());
                    }
                });
            }
        });
    }

}
