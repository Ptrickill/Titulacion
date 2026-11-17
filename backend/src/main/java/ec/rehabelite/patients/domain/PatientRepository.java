package ec.rehabelite.patients.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long>, PatientSearch {

    Optional<Patient> findByNationalId(String nationalId);

    /** The patient of a portal account; at most one (uq_patients_portal_user_id). */
    Optional<Patient> findByPortalUserId(Long portalUserId);

    /** Only the names of the patients, in one statement (HU-14). */
    @Query("SELECT p.id AS id, p.firstName AS firstName, p.lastName AS lastName FROM Patient p WHERE p.id IN :ids")
    List<PatientName> findNamesByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Patients without a portal account whose name (accent- and case-insensitive, via normalize_text) or
     * national ID prefix matches the query; an empty query matches every patient.
     */
    @Query(nativeQuery = true, value = """
            SELECT p.* FROM patients p
            WHERE p.portal_user_id IS NULL
              AND (normalize_text(p.last_name || ' ' || p.first_name) LIKE '%' || normalize_text(:query) || '%'
                   OR normalize_text(p.first_name || ' ' || p.last_name) LIKE '%' || normalize_text(:query) || '%'
                   OR p.national_id LIKE :query || '%')
            ORDER BY p.last_name, p.first_name
            LIMIT :limit""")
    List<Patient> findPortalCandidates(@Param("query") String query, @Param("limit") int limit);

}
