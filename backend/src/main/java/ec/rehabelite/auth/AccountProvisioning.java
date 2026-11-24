package ec.rehabelite.auth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public API of the auth module for the people registry (MEJ-02): accounts are created only for a registered person
 * (a staff record or a patient), with the email and the name of that record, never typed by hand. Every method runs
 * in the caller's transaction, so a failure rolls back the person's changes too. The auth module never depends on
 * its callers.
 */
public interface AccountProvisioning {

    /**
     * Creates an account pending activation, without a password, and audits CREATE User. The activation email is
     * not sent yet: the caller links the account to its person and then calls {@link #sendInvitation(Long)}, last.
     *
     * @param fullName "first names last names" of the person's record (MEJ-02: always derived, never typed)
     * @return the id of the new account
     * @throws ec.rehabelite.shared.domain.ConflictException if an account already uses the email, as its email or as
     *         a pending new email
     */
    Long create(String email, String fullName, Role role);

    /**
     * Emails the activation link of a new account and audits INVITATION_SENT User.
     *
     * @throws ec.rehabelite.shared.domain.ExternalServiceException if the email cannot be sent; the caller's
     *         transaction then rolls back, so nothing is created
     */
    void sendInvitation(Long userId);

    /** Keeps {@code users.full_name} equal to the person's names; nothing happens when it is already the same. */
    void rename(Long userId, String fullName);

    /**
     * Changes the email of an account after its person's email was edited, and audits EMAIL_CHANGE User without
     * either address:
     * <ul>
     * <li>pending activation: the account takes the new email and a new activation link is sent to it;</li>
     * <li>activated: the new email waits for confirmation through a link sent to it, the account keeps logging in
     * with the previous one meanwhile, and a notice without a link goes to the previous address (after the commit;
     * if the notice fails, the change goes ahead);</li>
     * <li>the same email it already has: a pending change, if any, is cancelled.</li>
     * </ul>
     *
     * @throws ec.rehabelite.shared.domain.BusinessRuleException if the account is deactivated
     * @throws ec.rehabelite.shared.domain.ConflictException if another account uses the email
     * @throws ec.rehabelite.shared.domain.ExternalServiceException if the link cannot be sent
     */
    void changeEmail(Long userId, String newEmail);

    Optional<AccountSummary> summary(Long userId);

    /** The accounts among the ids, in one statement; none for an empty collection. */
    Map<Long, AccountSummary> summaries(Collection<Long> userIds);

    /** Accounts that use the email as their email or as a pending new email (MEJ-02 uniqueness). */
    List<AccountSummary> accountsUsingEmail(String email);

    /** Ids of every account with the role, e.g. to filter the people registry by role. */
    List<Long> idsWithRole(Role role);

}
