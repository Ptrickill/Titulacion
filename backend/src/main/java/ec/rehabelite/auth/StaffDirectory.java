package ec.rehabelite.auth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public API of the auth module: lets other modules (clinicalrecords, sessions, ...) find staff accounts without
 * touching the users table.
 */
public interface StaffDirectory {

    /** The account when it exists, is active and has the PHYSIOTHERAPIST role; empty otherwise. */
    Optional<StaffMember> findActivePhysiotherapist(Long userId);

    /** Active physiotherapists ordered by full name. */
    List<StaffMember> activePhysiotherapists();

    /** Full name of each existing account among the ids, active or not (e.g. for history views). */
    Map<Long, String> fullNames(Collection<Long> userIds);

}
