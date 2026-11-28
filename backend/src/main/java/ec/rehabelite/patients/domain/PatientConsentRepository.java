package ec.rehabelite.patients.domain;

import ec.rehabelite.patients.ConsentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PatientConsentRepository extends JpaRepository<PatientConsent, Long> {

    /** Every consent of the patient, the most recent first: the history and the status of each type. */
    List<PatientConsent> findByPatientIdOrderByGrantedAtDescIdDesc(Long patientId);

    /** The open consent of a type: at most one (uq_patient_consents_current). */
    @Query("""
            SELECT c FROM PatientConsent c
            WHERE c.patientId = :patientId AND c.type = :type AND c.revokedAt IS NULL AND c.replacedAt IS NULL""")
    Optional<PatientConsent> findOpen(Long patientId, ConsentType type);

    @Query("""
            SELECT count(c) > 0 FROM PatientConsent c
            WHERE c.patientId = :patientId AND c.type = :type AND c.revokedAt IS NULL AND c.replacedAt IS NULL""")
    boolean existsOpen(Long patientId, ConsentType type);

    /** The open consents of the patient, of every type. */
    @Query("SELECT c FROM PatientConsent c WHERE c.patientId = :patientId AND c.revokedAt IS NULL "
            + "AND c.replacedAt IS NULL")
    List<PatientConsent> findAllOpen(Long patientId);

}
