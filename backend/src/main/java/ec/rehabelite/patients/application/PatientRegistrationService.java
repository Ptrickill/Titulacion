package ec.rehabelite.patients.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.patients.domain.PatientRepository;
import ec.rehabelite.shared.domain.ClinicCalendar;
import ec.rehabelite.shared.domain.ConflictException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patient admission (HU-05): registers the admission and personal data of a new patient and looks a patient up
 * by id. Never handles clinical data.
 */
@Service
@RequiredArgsConstructor
public class PatientRegistrationService {

    static final String AUDITED_ENTITY = "Patient";
    static final String PATIENT_NOT_FOUND = "El paciente no existe.";

    private static final Logger log = LoggerFactory.getLogger(PatientRegistrationService.class);

    private final PatientRepository patientRepository;
    private final PatientIdentityRules identityRules;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final ConsentService consentService;
    private final Clock clock;

    /**
     * Registers the patient with the national ID as medical record number and audits it as CREATE. Without an
     * admission date, today's date in the clinic is used. MEJ-06: the signed consents are recorded with it (CREATE
     * PatientConsent each); registering is already processing the data, so DATA_PROCESSING is required.
     *
     * @throws ConflictException if a patient or a staff member has the national ID, or the email belongs to a staff
     *         member or a staff account (MEJ-02, see {@link PatientIdentityRules})
     * @throws ec.rehabelite.shared.domain.BusinessRuleException if the birth date is after the admission date, or a
     *         consent version is not the one in force
     */
    @Transactional
    public Patient register(RegisterPatientCommand command) {
        identityRules.checkNationalId(command.nationalId());
        identityRules.checkEmail(command.personalData().email(), null);

        LocalDate admissionDate = command.admissionDate() != null
                ? command.admissionDate()
                : ClinicCalendar.today(clock);
        Long createdBy = currentUserProvider.currentUserId().orElse(null);
        Patient patient = patientRepository.save(Patient.register(command.nationalId(), admissionDate,
                command.personalData(), createdBy, clock.instant()));
        auditLogService.record(AuditAction.CREATE, AUDITED_ENTITY, patient.getId());
        consentService.grantAtRegistration(patient, command.dataProcessingConsentVersion(),
                command.emailCommunicationsConsentVersion());
        log.info("Patient registered patientId={} createdBy={}", patient.getId(), createdBy);
        return patient;
    }

    @Transactional(readOnly = true)
    public Patient findById(Long id) {
        return patientRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(PATIENT_NOT_FOUND));
    }

}
