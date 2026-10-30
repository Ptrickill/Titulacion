package ec.rehabelite.clinicalrecords.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.clinicalrecords.domain.ClinicalHistoryData;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecord;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecordRepository;
import ec.rehabelite.clinicalrecords.domain.Icd10Diagnosis;
import ec.rehabelite.clinicalrecords.domain.Icd10DiagnosisRepository;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.domain.TreatmentData;
import ec.rehabelite.clinicalrecords.domain.TreatmentRepository;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ClinicCalendar;
import ec.rehabelite.shared.domain.ConflictException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initial assessment (HU-07): creates the patient's clinical record with its first treatment, and opens new
 * treatments on an existing record. The authenticated physiotherapist becomes the current responsible of each
 * treatment. Patients are looked up only through {@link PatientDirectory}.
 */
@Service
@RequiredArgsConstructor
public class ClinicalRecordService {

    static final String CLINICAL_RECORD_ENTITY = "ClinicalRecord";
    static final String TREATMENT_ENTITY = "Treatment";
    static final String PATIENT_NOT_FOUND = "El paciente no existe.";
    static final String CLINICAL_RECORD_NOT_FOUND = "El historial clínico no existe.";
    static final String CLINICAL_RECORD_EXISTS = "El paciente ya tiene un historial clínico.";
    static final String DIAGNOSIS_NOT_FOUND = "El diagnóstico CIE-10 no existe en el catálogo.";
    /** Problem property with the id of the record the patient already has. */
    static final String EXISTING_CLINICAL_RECORD_ID = "existingClinicalRecordId";

    private static final Logger log = LoggerFactory.getLogger(ClinicalRecordService.class);

    private final ClinicalRecordRepository clinicalRecordRepository;
    private final TreatmentRepository treatmentRepository;
    private final Icd10DiagnosisRepository diagnosisRepository;
    private final PatientDirectory patientDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * Creates the clinical record and its first ACTIVE treatment, and audits both as CREATE.
     *
     * @throws ResourceNotFoundException if the patient does not exist
     * @throws ConflictException if the patient already has a clinical record (its id in existingClinicalRecordId)
     * @throws BusinessRuleException if the diagnosis is not in the catalog or a value is out of range
     */
    @Transactional
    public ClinicalRecord create(Long patientId, ClinicalHistoryData history, String diagnosisCode,
            TreatmentData treatment) {
        if (patientDirectory.find(patientId).isEmpty()) {
            throw new ResourceNotFoundException(PATIENT_NOT_FOUND);
        }
        clinicalRecordRepository.findByPatientId(patientId).ifPresent(existing -> {
            throw new ConflictException(CLINICAL_RECORD_EXISTS, Map.of(EXISTING_CLINICAL_RECORD_ID, existing.getId()));
        });
        Icd10Diagnosis diagnosis = diagnosis(diagnosisCode);
        Long physiotherapistId = currentPhysiotherapistId();
        Instant now = clock.instant();

        ClinicalRecord record = ClinicalRecord.create(patientId, history, physiotherapistId, now);
        Treatment first = record.openTreatment(diagnosis, treatment, physiotherapistId, ClinicCalendar.today(clock),
                now);
        clinicalRecordRepository.save(record);

        auditLogService.record(AuditAction.CREATE, CLINICAL_RECORD_ENTITY, record.getId());
        auditLogService.record(AuditAction.CREATE, TREATMENT_ENTITY, first.getId());
        log.info("Clinical record created clinicalRecordId={} patientId={} treatmentId={} createdBy={}",
                record.getId(), patientId, first.getId(), physiotherapistId);
        return record;
    }

    /**
     * Opens a new ACTIVE treatment on an existing record and audits it as CREATE.
     *
     * @throws ResourceNotFoundException if the clinical record does not exist
     * @throws BusinessRuleException if a treatment is still active, the diagnosis is not in the catalog or a value
     *         is out of range
     */
    @Transactional
    public Treatment openTreatment(Long clinicalRecordId, String diagnosisCode, TreatmentData treatment) {
        ClinicalRecord record = clinicalRecordRepository.findById(clinicalRecordId)
                .orElseThrow(() -> new ResourceNotFoundException(CLINICAL_RECORD_NOT_FOUND));
        Icd10Diagnosis diagnosis = diagnosis(diagnosisCode);
        Long physiotherapistId = currentPhysiotherapistId();

        Treatment opened = record.openTreatment(diagnosis, treatment, physiotherapistId, ClinicCalendar.today(clock),
                clock.instant());
        // The record is already managed: persist the new treatment itself (saving the record would merge it, and
        // merge does not cascade to a new treatment)
        treatmentRepository.save(opened);

        auditLogService.record(AuditAction.CREATE, TREATMENT_ENTITY, opened.getId());
        log.info("Treatment opened treatmentId={} clinicalRecordId={} createdBy={}", opened.getId(),
                record.getId(), physiotherapistId);
        return opened;
    }

    private Icd10Diagnosis diagnosis(String code) {
        String normalized = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        return diagnosisRepository.findById(normalized)
                .orElseThrow(() -> new BusinessRuleException(DIAGNOSIS_NOT_FOUND));
    }

    private Long currentPhysiotherapistId() {
        return currentUserProvider.currentUserId()
                .orElseThrow(() -> new IllegalStateException("A clinical record needs an authenticated user"));
    }

}
