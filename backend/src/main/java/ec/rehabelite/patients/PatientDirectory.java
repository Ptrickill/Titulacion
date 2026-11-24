package ec.rehabelite.patients;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public API of the patients module: how other modules (clinicalrecords, sessions, ...) find patients. They never
 * use the module's web, application or domain packages.
 */
public interface PatientDirectory {

    Optional<PatientCard> find(Long patientId);

    /**
     * The patient linked to a portal account ({@code patients.portal_user_id}); empty when the account has none.
     * The patient portal (HU-12) identifies its patient only this way, never by an id sent by the client.
     */
    Optional<PatientCard> findByPortalUser(Long userId);

    /** Personal and contact data, for the full clinical record view (HU-09). */
    Optional<PatientProfile> findProfile(Long patientId);

    /**
     * Same search as GET /api/patients (HU-06): digits match the beginning of the national ID, otherwise every word
     * must appear in the name ignoring accents and case; ordered by last name. The pageable's sort is ignored.
     */
    Page<PatientCard> search(String query, Pageable pageable);

    /**
     * The full name ("first names last names") of each existing patient among the ids, and nothing else of their
     * personal data (HU-14: data minimization). One statement; none for an empty collection.
     */
    Map<Long, String> fullNames(Collection<Long> patientIds);

    /** The patient with that national ID (MEJ-02: unique between patients and staff). */
    Optional<Long> findIdByNationalId(String nationalId);

    /**
     * The patients with that email (MEJ-02). Patients may share an email (e.g. a minor and a parent), but the email
     * of a staff member must not be any patient's.
     */
    List<Long> idsWithEmail(String email);

    /**
     * The patients of the people registry (MEJ-02) from an offset, with the same search and order as
     * {@link #search}, and the total that match. Two statements.
     */
    PatientRegistrySlice registry(String query, long offset, int limit);

}
