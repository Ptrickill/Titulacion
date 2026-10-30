package ec.rehabelite.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ec.rehabelite.auth.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTests {

    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");

    private final User user = User.create("  Ana.Torres@RehabElite.test ", "hash", "Ana Torres", Role.PHYSIOTHERAPIST,
            null, NOW);

    @Test
    void createsAnActiveAccountWithNormalizedEmail() {
        assertThat(user.getEmail()).isEqualTo("ana.torres@rehabelite.test");
        assertThat(user.isActive()).isTrue();
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.isLocked(NOW)).isFalse();
    }

    @Test
    void locksForFifteenMinutesOnTheFifthFailedAttempt() {
        for (int attempt = 1; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            user.registerFailedAttempt(NOW);
            assertThat(user.isLocked(NOW)).isFalse();
        }

        user.registerFailedAttempt(NOW);

        assertThat(user.getFailedAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isEqualTo(NOW.plusSeconds(15 * 60));
        assertThat(user.isLocked(NOW.plusSeconds(15 * 60 - 1))).isTrue();
        assertThat(user.isLocked(NOW.plusSeconds(15 * 60))).isFalse();
    }

    @Test
    void startsANewCountOnceTheLockHasExpired() {
        for (int attempt = 0; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            user.registerFailedAttempt(NOW);
        }
        Instant afterLock = NOW.plusSeconds(16 * 60);

        user.registerFailedAttempt(afterLock);

        assertThat(user.getFailedAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void successfulLoginResetsTheCounterAndStoresTheTime() {
        user.registerFailedAttempt(NOW);
        user.registerFailedAttempt(NOW);
        Instant later = NOW.plusSeconds(30);

        user.registerSuccessfulLogin(later);

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(later);
    }

    @Test
    void deactivationKeepsTheAccountAndRecordsWhoChangedIt() {
        Instant later = NOW.plusSeconds(60);

        user.deactivate(7L, later);

        assertThat(user.isActive()).isFalse();
        assertThat(user.getEmail()).isEqualTo("ana.torres@rehabelite.test");
        assertThat(user.getUpdatedBy()).isEqualTo(7L);
        assertThat(user.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void reactivationStartsWithoutFailedAttemptsOrLock() {
        for (int attempt = 0; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            user.registerFailedAttempt(NOW);
        }
        user.deactivate(7L, NOW);
        Instant later = NOW.plusSeconds(60);

        user.activate(8L, later);

        assertThat(user.isActive()).isTrue();
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.isLocked(later)).isFalse();
        assertThat(user.getUpdatedBy()).isEqualTo(8L);
        assertThat(user.getUpdatedAt()).isEqualTo(later);
    }

}
