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

    /** Patients with that email, already normalized (MEJ-02); patients may share one. */
    @Query("SELECT p.id FROM Patient p WHERE p.email = :email ORDER BY p.id")
    List<Long> findIdsByEmail(@Param("email") String email);

}
