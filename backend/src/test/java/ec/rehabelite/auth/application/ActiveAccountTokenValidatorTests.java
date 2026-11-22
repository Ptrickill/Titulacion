package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.rehabelite.auth.domain.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ActiveAccountTokenValidatorTests {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ActiveAccountTokenValidator validator = new ActiveAccountTokenValidator(userRepository);

    @Test
    void acceptsTheTokenOfAnActiveAccountWithTheCurrentSessionVersion() {
        when(userRepository.existsByIdAndActiveTrueAndSessionVersion(12L, 2)).thenReturn(true);

        assertThat(validator.validate(token("12", 2)).hasErrors()).isFalse();
    }

    @Test
    void rejectsTheTokenOfAnInactiveOrDeletedAccountOrOneIssuedBeforeAPasswordReset() {
        when(userRepository.existsByIdAndActiveTrueAndSessionVersion(12L, 1)).thenReturn(false);

        assertThat(validator.validate(token("12", 1)).getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
        verify(userRepository).existsByIdAndActiveTrueAndSessionVersion(12L, 1);
    }

    @Test
    void readsATokenWithoutTheSessionVersionClaimAsVersionZero() {
        Instant now = Instant.parse("2026-10-29T14:00:00Z");
        Jwt legacy = Jwt.withTokenValue("token").header("alg", "HS256").subject("12")
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        when(userRepository.existsByIdAndActiveTrueAndSessionVersion(12L, 0)).thenReturn(true);

        assertThat(validator.validate(legacy).hasErrors()).isFalse();
    }

    @Test
    void rejectsASubjectThatIsNotAUserId() {
        assertThat(validator.validate(token("not-an-id", 0)).hasErrors()).isTrue();
        verify(userRepository, never()).existsByIdAndActiveTrueAndSessionVersion(any(), anyInt());
    }

    private static Jwt token(String subject, int sessionVersion) {
        Instant now = Instant.parse("2026-10-29T14:00:00Z");
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(subject)
                .claim(JwtClaims.SESSION_VERSION, (long) sessionVersion)
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
    }

}
