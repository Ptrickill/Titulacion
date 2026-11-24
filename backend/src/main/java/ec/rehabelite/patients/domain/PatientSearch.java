package ec.rehabelite.patients.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Name or national ID search of patients (HU-06), implemented with native SQL in {@link PatientSearchImpl}. */
public interface PatientSearch {

    Page<Patient> search(String query, Pageable pageable);

    /**
     * Same search and order from any offset, not only a page boundary (MEJ-02 people registry), optionally only the
     * patients without a portal account or with one of the given accounts.
     *
     * @param portalUserIds only these portal accounts, or every patient when null
     */
    List<Patient> searchSlice(String query, boolean withoutPortalAccount, Collection<Long> portalUserIds,
            long offset, int limit);

    /** How many patients match the search and the portal account filter. */
    long countSearch(String query, boolean withoutPortalAccount, Collection<Long> portalUserIds);

}
