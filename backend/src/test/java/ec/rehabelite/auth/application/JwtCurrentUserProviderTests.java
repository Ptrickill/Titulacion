package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.auth.CurrentUser;
import ec.rehabelite.auth.Role;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtCurrentUserProviderTests {

    private final JwtCurrentUserProvider provider = new JwtCurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsTheUserFromTheJwtClaims() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("15")
                .claim(JwtClaims.EMAIL, "carlos.vera@rehabelite.test")
                .claim(JwtClaims.FULL_NAME, "Carlos Vera")
                .claim(JwtClaims.ROLE, "OPERATIONS_DIRECTOR")
                .issuedAt(Instant.now())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_OPERATIONS_DIRECTOR")));

        assertThat(provider.currentUser()).contains(
                new CurrentUser(15L, "carlos.vera@rehabelite.test", "Carlos Vera", Role.OPERATIONS_DIRECTOR));
        assertThat(provider.currentUserId()).contains(15L);
    }

    @Test
    void isEmptyForAnonymousRequests() {
        assertThat(provider.currentUser()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(provider.currentUser()).isEmpty();
    }

}
