package ec.rehabelite.clinicalrecords.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicalRecordRepository extends JpaRepository<ClinicalRecord, Long> {

    Optional<ClinicalRecord> findByPatientId(Long patientId);

    /**
     * One row per patient that has a clinical record, with its current treatment: the active one, otherwise the
     * most recent (by start date, then id). One query for a whole page of patients.
     */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (cr.patient_id)
                   cr.patient_id       AS "patientId",
                   cr.id               AS "clinicalRecordId",
                   t.id                AS "treatmentId",
                   d.code              AS "diagnosisCode",
                   d.description       AS "diagnosisDescription",
                   t.severity          AS "severity",
                   t.planned_sessions  AS "plannedSessions",
                   t.status            AS "status"
            FROM clinical_records cr
            LEFT JOIN treatments t ON t.clinical_record_id = cr.id
            LEFT JOIN icd10_diagnoses d ON d.code = t.diagnosis_code
            WHERE cr.patient_id IN (:patientIds)
            ORDER BY cr.patient_id, (t.status = 'ACTIVE') DESC NULLS LAST, t.start_date DESC NULLS LAST,
                     t.id DESC NULLS LAST""")
    List<CurrentTreatmentView> findCurrentTreatments(@Param("patientIds") Collection<Long> patientIds);

}
