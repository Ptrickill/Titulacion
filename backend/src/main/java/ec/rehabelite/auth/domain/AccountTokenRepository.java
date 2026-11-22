package ec.rehabelite.auth.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    /** Locked until the end of the transaction, so two simultaneous uses of one link cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM AccountToken t WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose")
    Optional<AccountToken> findForUse(@Param("tokenHash") String tokenHash,
            @Param("purpose") AccountTokenPurpose purpose);

    /** Replaces the unused links of a purpose when a new one is issued. */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE AccountToken t SET t.usedAt = :now
            WHERE t.userId = :userId AND t.purpose = :purpose AND t.usedAt IS NULL""")
    int markUnusedAsUsed(@Param("userId") Long userId, @Param("purpose") AccountTokenPurpose purpose,
            @Param("now") Instant now);

    /** Links of a purpose issued since a moment, used or not (rate limit of the reset requests). */
    long countByUserIdAndPurposeAndCreatedAtAfter(Long userId, AccountTokenPurpose purpose, Instant since);

}
