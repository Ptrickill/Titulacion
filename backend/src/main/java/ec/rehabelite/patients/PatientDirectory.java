package ec.rehabelite.patients;

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

}
