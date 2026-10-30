package ec.rehabelite.patients.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.patients.domain.PatientRepository;
import ec.rehabelite.patients.domain.PersonalData;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patient search and update of personal data (HU-06). The national ID and the medical record number never change,
 * and clinical data is never handled here.
 */
@Service
@RequiredArgsConstructor
public class PatientManagementService {

    private static final Logger log = LoggerFactory.getLogger(PatientManagementService.class);

    private final PatientRepository patientRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /** Partial, accent- and case-insensitive search by name, or by the beginning of the national ID. */
    @Transactional(readOnly = true)
    public Page<Patient> search(String query, Pageable pageable) {
        return patientRepository.search(query, pageable);
    }

    /**
     * Replaces the admission date and the personal data, recording who and when (updated_by, updated_at) and
     * auditing UPDATE. Sending the data the patient already has changes nothing and is not audited.
     *
     * @throws ResourceNotFoundException if the patient does not exist
     * @throws ec.rehabelite.shared.domain.BusinessRuleException if the birth date is after the admission date
     */
    @Transactional
    public Patient update(Long patientId, LocalDate admissionDate, PersonalData data) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(PatientRegistrationService.PATIENT_NOT_FOUND));
        Long updatedBy = currentUserProvider.currentUserId().orElse(null);
        if (patient.update(admissionDate, data, updatedBy, clock.instant())) {
            auditLogService.record(AuditAction.UPDATE, PatientRegistrationService.AUDITED_ENTITY, patient.getId());
            log.info("Patient updated patientId={} updatedBy={}", patient.getId(), updatedBy);
        }
        return patient;
    }

}
