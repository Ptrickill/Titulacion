package ec.rehabelite.auth.application;

import ec.rehabelite.auth.domain.AccountToken;
import ec.rehabelite.auth.domain.AccountTokenPurpose;
import ec.rehabelite.auth.domain.AccountTokenRepository;
import ec.rehabelite.shared.config.AppProperties;
import ec.rehabelite.shared.domain.InvalidRequestException;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and spends the one-time account links (MEJ-01). A link works once and until it expires (activation:
 * {@code app.tokens.activation-ttl}, reset: {@code app.tokens.reset-ttl}); issuing a new link of a purpose
 * replaces the unused ones. Runs in the caller's transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
class AccountTokenService {

    /** Same answer for a link that never existed, was used, was replaced or expired. */
    static final String INVALID_LINK = "El enlace no es válido o ya venció. Solicite uno nuevo.";

    private final AccountTokenRepository tokenRepository;
    private final AppProperties properties;

    /** Stores the hash of a new token for the account and returns the raw token for the emailed link. */
    String issue(Long userId, AccountTokenPurpose purpose, Instant now) {
        tokenRepository.markUnusedAsUsed(userId, purpose, now);
        String token = AccountTokens.generate();
        tokenRepository.save(AccountToken.issue(userId, purpose, AccountTokens.sha256Hex(token), now,
                now.plus(ttlOf(purpose))));
        return token;
    }

    /**
     * Spends a token: it must exist for that purpose, be unused and not be expired. The row stays locked until the
     * end of the transaction, so a link cannot be used twice at the same time.
     *
     * @return the id of the account the link belongs to
     * @throws InvalidRequestException with the same generic message in every failure case
     */
    Long consume(String token, AccountTokenPurpose purpose, Instant now) {
        if (token == null || token.isBlank()) {
            throw new InvalidRequestException(INVALID_LINK);
        }
        AccountToken stored = tokenRepository.findForUse(AccountTokens.sha256Hex(token.trim()), purpose)
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new InvalidRequestException(INVALID_LINK));
        stored.markUsed(now);
        return stored.getUserId();
    }

    /** Links of a purpose issued for the account since a moment, used or not. */
    long countIssuedSince(Long userId, AccountTokenPurpose purpose, Instant since) {
        return tokenRepository.countByUserIdAndPurposeAndCreatedAtAfter(userId, purpose, since);
    }

    private Duration ttlOf(AccountTokenPurpose purpose) {
        return switch (purpose) {
            case ACTIVATION -> properties.tokens().activationTtl();
            case PASSWORD_RESET -> properties.tokens().resetTtl();
        };
    }

}
