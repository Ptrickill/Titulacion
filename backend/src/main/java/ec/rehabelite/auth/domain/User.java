package ec.rehabelite.auth.domain;

import ec.rehabelite.auth.AccountStatus;
import ec.rehabelite.auth.AccountSummary;
import ec.rehabelite.auth.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * System account. The lockout policy lives here: after {@value #MAX_FAILED_ATTEMPTS} consecutive failed
 * logins the account is locked for {@link #LOCK_DURATION}.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    /** BCrypt hash; null while the account is pending activation (MEJ-01). */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** When the owner activated the account through the emailed link; null while pending (MEJ-01). */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    /** Carried by the access tokens (claim "sv"); a password reset increments it so older tokens stop working. */
    @Column(name = "session_version", nullable = false)
    private int sessionVersion;

    /**
     * New email of an activated account waiting for its owner's confirmation (MEJ-02); null when no change is
     * pending. Until it is confirmed the account keeps logging in with {@link #email}.
     */
    @Column(name = "pending_email", length = 254)
    private String pendingEmail;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * New active account that already has its password and a verified email, e.g. the initial administrator. The
     * email is stored trimmed and in lower case.
     */
    public static User create(String email, String passwordHash, String fullName, Role role, Long createdBy,
            Instant now) {
        User user = invite(email, fullName, role, createdBy, now);
        user.passwordHash = passwordHash;
        user.emailVerifiedAt = now;
        return user;
    }

    /**
     * New account pending activation (MEJ-01): no password and no verified email until its owner follows the
     * activation link. It cannot log in meanwhile.
     */
    public static User invite(String email, String fullName, Role role, Long createdBy, Instant now) {
        User user = new User();
        user.email = normalizeEmail(email);
        user.fullName = fullName;
        user.role = role;
        user.active = true;
        user.failedAttempts = 0;
        user.sessionVersion = 0;
        user.createdBy = createdBy;
        user.createdAt = now;
        return user;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** The owner has not activated the account yet (no password, email not verified). */
    public boolean isPendingActivation() {
        return emailVerifiedAt == null;
    }

    /** INACTIVE when deactivated, whether or not it was ever activated; otherwise pending or active. */
    public AccountStatus status() {
        if (!active) {
            return AccountStatus.INACTIVE;
        }
        return isPendingActivation() ? AccountStatus.PENDING_ACTIVATION : AccountStatus.ACTIVE;
    }

    public AccountSummary summary() {
        return new AccountSummary(id, role, status(), pendingEmail != null, lastLoginAt);
    }

    /**
     * Keeps the account name equal to its person's names (MEJ-02).
     *
     * @return whether it changed
     */
    public boolean rename(String fullName, Long updatedBy, Instant now) {
        if (fullName.equals(this.fullName)) {
            return false;
        }
        this.fullName = fullName;
        markUpdated(updatedBy, now);
        return true;
    }

    /** MEJ-02: an account not activated yet takes the new email directly; its new activation link goes there. */
    public void changeEmailBeforeActivation(String email, Long updatedBy, Instant now) {
        this.email = normalizeEmail(email);
        this.pendingEmail = null;
        markUpdated(updatedBy, now);
    }

    /** MEJ-02: an activated account keeps its email until the owner confirms the new one. */
    public void requestEmailChange(String email, Long updatedBy, Instant now) {
        this.pendingEmail = normalizeEmail(email);
        markUpdated(updatedBy, now);
    }

    /** MEJ-02: the person's email went back to the account's current email: nothing waits for confirmation. */
    public void cancelEmailChange(Long updatedBy, Instant now) {
        this.pendingEmail = null;
        markUpdated(updatedBy, now);
    }

    /**
     * MEJ-02: the owner confirmed the new email through the link. It becomes the login email, verified now, and the
     * sessions opened with the previous email are closed (see {@link #getSessionVersion()}).
     */
    public void confirmEmailChange(Instant now) {
        this.email = pendingEmail;
        this.pendingEmail = null;
        this.emailVerifiedAt = now;
        this.sessionVersion++;
        markUpdated(null, now);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Counts a wrong password and locks the account when the limit is reached. A lock that has already
     * expired starts a new count.
     */
    public void registerFailedAttempt(Instant now) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            failedAttempts = 0;
            lockedUntil = null;
        }
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** Lets the account log in again; it starts with a clean failed-attempt count and no lock. */
    public void activate(Long updatedBy, Instant now) {
        active = true;
        failedAttempts = 0;
        lockedUntil = null;
        markUpdated(updatedBy, now);
    }

    /** Blocks the login and the tokens already issued; the account and its history are kept. */
    public void deactivate(Long updatedBy, Instant now) {
        active = false;
        markUpdated(updatedBy, now);
    }

    /**
     * Activation through the emailed link (MEJ-01): stores the first password and marks the email as verified.
     * The owner acts without a session, so there is no updating user.
     */
    public void completeActivation(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.emailVerifiedAt = now;
        markUpdated(null, now);
    }

    /**
     * Password reset through the emailed link (MEJ-01): stores the new password, clears the failed attempts and
     * the lock, and invalidates every access token issued before (see {@link #getSessionVersion()}).
     */
    public void resetPassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.sessionVersion++;
        markUpdated(null, now);
    }

    /** Stores a new BCrypt password hash, e.g. the development seed accounts (TEC-06). */
    public void changePasswordHash(String passwordHash, Long updatedBy, Instant now) {
        this.passwordHash = passwordHash;
        markUpdated(updatedBy, now);
    }

    private void markUpdated(Long updatedBy, Instant now) {
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

}
