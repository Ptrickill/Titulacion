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
 * Rejects tokens whose account no longer exists or has been deactivated (HU-03), so a deactivation takes
 * effect immediately instead of when the token expires. The request then gets the usual 401.
 */
@Component
@RequiredArgsConstructor
public class ActiveAccountTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INACTIVE_ACCOUNT = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "The account is inactive or no longer exists", null);

    private final UserRepository userRepository;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Long userId = parseUserId(token.getSubject());
        if (userId != null && userRepository.existsByIdAndActiveTrue(userId)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INACTIVE_ACCOUNT);
    }

    private static Long parseUserId(String subject) {
        try {
            return subject == null ? null : Long.valueOf(subject);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}
