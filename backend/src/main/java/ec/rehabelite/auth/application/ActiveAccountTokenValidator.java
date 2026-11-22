package ec.rehabelite.auth.application;

import ec.rehabelite.auth.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Rejects tokens whose account no longer exists or has been deactivated (HU-03), or that were issued before the
 * last password reset (MEJ-01: their session version is older), so both take effect immediately instead of when
 * the token expires. The request then gets the usual 401.
 */
@Component
@RequiredArgsConstructor
public class ActiveAccountTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INACTIVE_ACCOUNT = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "The account is inactive, no longer exists or its password was reset", null);

    private final UserRepository userRepository;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Long userId = parseUserId(token.getSubject());
        if (userId != null
                && userRepository.existsByIdAndActiveTrueAndSessionVersion(userId, sessionVersion(token))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INACTIVE_ACCOUNT);
    }

    /** Tokens issued before MEJ-01 have no claim: they belong to version 0. */
    private static int sessionVersion(Jwt token) {
        Object claim = token.getClaims().get(JwtClaims.SESSION_VERSION);
        return claim instanceof Number number ? number.intValue() : 0;
    }

    private static Long parseUserId(String subject) {
        try {
            return subject == null ? null : Long.valueOf(subject);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}
