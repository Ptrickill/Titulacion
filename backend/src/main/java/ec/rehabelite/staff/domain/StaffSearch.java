package ec.rehabelite.staff.domain;

import java.util.Collection;
import java.util.List;

/**
 * Search of staff records for the people registry (MEJ-02), implemented with native SQL in {@link StaffSearchImpl}.
 * Same rules as the patient search (HU-06): digits match the beginning of the national ID, otherwise every word must
 * appear in the names, ignoring accents and case; ordered by last name and first name.
 */
public interface StaffSearch {

    /**
     * @param userIds only the records of these accounts (e.g. those with a role), or every record when null
     */
    List<StaffRecord> searchSlice(String query, Collection<Long> userIds, long offset, int limit);

    long countSearch(String query, Collection<Long> userIds);

}
