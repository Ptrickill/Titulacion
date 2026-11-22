package ec.rehabelite.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One-time link of an account (MEJ-01). Only the SHA-256 of the token is stored: whoever reads the table cannot
 * rebuild a link. A token works once, until {@code expiresAt}; a newer token of the same purpose replaces it.
 */
@Entity
@Table(name = "account_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AccountTokenPurpose purpose;

    /** Hex SHA-256 of the token sent by email. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** When the link was used, or replaced by a newer one; null while it can still be used. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AccountToken issue(Long userId, AccountTokenPurpose purpose, String tokenHash, Instant now,
            Instant expiresAt) {
        AccountToken token = new AccountToken();
        token.userId = userId;
        token.purpose = purpose;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = expiresAt;
        return token;
    }

    /** Not used, not replaced and not expired. */
    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    /** Marks the link as spent: after its use, or when a newer link of the same purpose replaces it. */
    public void markUsed(Instant now) {
        if (usedAt == null) {
            usedAt = now;
        }
    }

}
