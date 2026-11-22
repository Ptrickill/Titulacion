package ec.rehabelite.auth.application;

import ec.rehabelite.auth.domain.User;
import ec.rehabelite.shared.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues HS256 access tokens whose claims identify the user: id (subject), email, full name, role and the session
 * version (MEJ-01).
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final AppProperties properties;

    public LoginResult issue(User user, Instant now) {
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.jwt().expirationMinutes()));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtClaims.ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(JwtClaims.EMAIL, user.getEmail())
                .claim(JwtClaims.FULL_NAME, user.getFullName())
                .claim(JwtClaims.ROLE, user.getRole().name())
                .claim(JwtClaims.SESSION_VERSION, user.getSessionVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResult(token, expiresAt, user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }

}
