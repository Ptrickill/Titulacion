package ec.rehabelite.audit;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Names of the users that appear in the audit log (HU-18). The audit module declares it and the auth module, which
 * already depends on audit, implements it: audit never depends on auth.
 */
public interface AuditUserDirectory {

    /** Full names by user id, also of deactivated accounts; unknown ids are left out. */
    Map<Long, String> fullNames(Collection<Long> userIds);

    /** Every account (any role, active or not) ordered by name, for the user filter of the audit log. */
    List<AuditUser> users();

}
