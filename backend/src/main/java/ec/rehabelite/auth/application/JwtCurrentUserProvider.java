package ec.rehabelite.auth.application;

import ec.rehabelite.auth.CurrentUser;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.Role;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Reads the current user from the validated JWT of the request.
 */
@Component
class JwtCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<CurrentUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            return Optional.empty();
        }
        Jwt jwt = token.getToken();
        return Optional.of(new CurrentUser(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString(JwtClaims.EMAIL),
                jwt.getClaimAsString(JwtClaims.FULL_NAME),
                Role.valueOf(jwt.getClaimAsString(JwtClaims.ROLE))));
    }

}
