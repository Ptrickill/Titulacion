package ec.rehabelite.auth;

import java.util.Collection;
import java.util.Map;

/**
 * Accounts of each role (MEJ-02, MEJ-05): how many there are in any status, how many are active (enabled and
 * activated) and how many wait for activation. The reception builds its portal indicators from the PATIENT role and
 * the operations director the staff indicators from the other roles.
 */
public record AccountStatistics(Map<Role, RoleAccounts> byRole) {

    public AccountStatistics {
        byRole = Map.copyOf(byRole);
    }

    /** The accounts of one role; a role without accounts counts zero. */
    public record RoleAccounts(long accounts, long active, long pendingActivation) {

        static final RoleAccounts NONE = new RoleAccounts(0, 0, 0);

    }

    public RoleAccounts of(Role role) {
        return byRole.getOrDefault(role, RoleAccounts.NONE);
    }

    /** Active accounts (enabled and activated) of those roles. */
    public long activeOf(Collection<Role> roles) {
        return roles.stream().mapToLong(role -> of(role).active()).sum();
    }

}
