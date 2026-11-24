package ec.rehabelite.staff;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public API of the staff module (MEJ-04): the weekly schedules of the physiotherapists, for the operations director's
 * board, availability and treatment list (module assignments). Read only: schedules change only through
 * {@code PUT /api/staff/{id}/schedule}. Whether an account is active is asked to auth, not here.
 */
public interface PhysiotherapistSchedules {

    /**
     * Every physiotherapist with a staff record, active or not, with its slots, ordered by last names and first
     * names (Spanish collation). Three statements, whatever the number of physiotherapists.
     */
    List<PhysiotherapistSchedule> all();

    /**
     * The days on which each account among the ids has at least one slot, in one statement. An account without
     * slots (or without a staff record) is not a key.
     */
    Map<Long, Set<DayOfWeek>> workingDays(Collection<Long> userIds);

}
