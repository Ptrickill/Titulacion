package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTests {

    private static final String EMAIL = "lucia.mora@rehabelite.test";
    private static final String PASSWORD = "Clave.Correcta2026";
    private static final Long USER_ID = 42L;

    // Low cost factor: these tests check the flow, not BCrypt itself
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(4);
    private static final String PASSWORD_HASH = PASSWORD_ENCODER.encode(PASSWORD);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtTokenService tokenService = mock(JwtTokenService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-28T15:00:00Z"));

    private final AuthService authService = new AuthService(userRepository, PASSWORD_ENCODER, tokenService,
            auditLogService, clock);

    private User user;

    @BeforeEach
    void setUp() {
        user = User.create(EMAIL, PASSWORD_HASH, "Lucía Mora", Role.PHYSIOTHERAPIST, null, clock.instant());
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenService.issue(any(User.class), any(Instant.class))).thenAnswer(invocation -> new LoginResult(
                "signed-token", clock.instant().plusSeconds(1800), USER_ID, EMAIL, "Lucía Mora", Role.PHYSIOTHERAPIST));
    }

    @Test
    void validCredentialsReturnTheTokenUpdateLastLoginAndAuditTheLogin() {
        LoginResult result = authService.login(" Lucia.Mora@RehabElite.test ", PASSWORD);

        assertThat(result.token()).isEqualTo("signed-token");
        assertThat(result.role()).isEqualTo(Role.PHYSIOTHERAPIST);
        assertThat(user.getLastLoginAt()).isEqualTo(clock.instant());
        assertThat(user.getFailedAttempts()).isZero();
        verify(auditLogService).recordFor(USER_ID, AuditAction.LOGIN, AuthService.AUDITED_ENTITY, USER_ID);
    }

    @Test
    void wrongPasswordIsRejectedAndCounted() {
        assertThatThrownBy(() -> authService.login(EMAIL, "Clave.Incorrecta"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(InvalidCredentialsException.USER_MESSAGE);

        assertThat(user.getFailedAttempts()).isEqualTo(1);
        assertThat(user.getLastLoginAt()).isNull();
        verifyNoLoginRecorded();
    }

    @Test
    void unknownEmailGetsTheSameGenericError() {
        when(userRepository.findByEmail("nadie@rehabelite.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nadie@rehabelite.test", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(InvalidCredentialsException.USER_MESSAGE);
        verifyNoLoginRecorded();
    }

    @Test
    void inactiveAccountIsRejectedEvenWithTheRightPassword() {
        ReflectionTestUtils.setField(user, "active", false);

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD)).isInstanceOf(InvalidCredentialsException.class);
        assertThat(user.getFailedAttempts()).isZero();
        verifyNoLoginRecorded();
    }

    @Test
    void sixthAttemptIsRejectedEvenWithTheRightPassword() {
        for (int attempt = 0; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> authService.login(EMAIL, "Clave.Incorrecta"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(user.isLocked(clock.instant())).isTrue();

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(InvalidCredentialsException.USER_MESSAGE);
        assertThat(user.getFailedAttempts()).isEqualTo(User.MAX_FAILED_ATTEMPTS);
        verifyNoLoginRecorded();
    }

    @Test
    void accountCanLogInAgainAfterFifteenMinutes() {
        for (int attempt = 0; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> authService.login(EMAIL, "Clave.Incorrecta"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        clock.advanceSeconds(15 * 60);

        LoginResult result = authService.login(EMAIL, PASSWORD);

        assertThat(result.token()).isEqualTo("signed-token");
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void passwordLongerThanBcryptAllowsIsRejectedAsInvalid() {
        assertThatThrownBy(() -> authService.login(EMAIL, "x".repeat(100)))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(user.getFailedAttempts()).isEqualTo(1);
    }

    private void verifyNoLoginRecorded() {
        verify(auditLogService, never()).recordFor(anyLong(), any(AuditAction.class), anyString(), anyLong());
        verify(tokenService, never()).issue(any(User.class), any(Instant.class));
    }

    /** Clock the tests can move forward. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

    }

}
