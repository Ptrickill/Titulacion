package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void acceptsTheTokenOfAnActiveAccount() {
        when(userRepository.existsByIdAndActiveTrue(12L)).thenReturn(true);

        assertThat(validator.validate(token("12")).hasErrors()).isFalse();
    }

    @Test
    void rejectsTheTokenOfAnInactiveOrDeletedAccount() {
        when(userRepository.existsByIdAndActiveTrue(12L)).thenReturn(false);

        assertThat(validator.validate(token("12")).getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
        verify(userRepository).existsByIdAndActiveTrue(12L);
    }

    @Test
    void rejectsASubjectThatIsNotAUserId() {
        assertThat(validator.validate(token("not-an-id")).hasErrors()).isTrue();
        verify(userRepository, never()).existsByIdAndActiveTrue(any());
    }

    private static Jwt token(String subject) {
        Instant now = Instant.parse("2026-10-29T14:00:00Z");
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(subject)
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
    }

}
