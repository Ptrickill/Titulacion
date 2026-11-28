package ec.rehabelite.patients.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.AccountProvisioning;
import ec.rehabelite.auth.AccountSummary;
import ec.rehabelite.auth.Role;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.patients.domain.PatientRepository;
import ec.rehabelite.shared.domain.ConflictException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portal account of a patient (MEJ-02): created from the patient's record ("Crear acceso al portal") with the
 * patient's email and names, never typed by hand.
 */
@Service
@RequiredArgsConstructor
public class PortalAccountService {

    static final String EMAIL_IN_USE = "Ya existe una cuenta con el correo de este paciente. Corrija el correo del "
            + "paciente antes de crear su acceso al portal.";
    static final String EXISTING_USER_ID = "existingUserId";

    private static final Logger log = LoggerFactory.getLogger(PortalAccountService.class);

    private final PatientRepository patientRepository;
    private final AccountProvisioning accounts;
    private final AuditLogService auditLogService;
    private final ConsentService consentService;

    /**
     * Creates the PATIENT account with the patient's email and names, links it to the patient and emails the
     * activation link (MEJ-01), in one transaction. Audited as CREATE User, UPDATE Patient and INVITATION_SENT User.
     *
     * @throws ResourceNotFoundException if the patient does not exist
     * @throws ConflictException if the patient already has an account, or another account uses the patient's email
     *         (as its email or as a pending new email)
     * @throws ec.rehabelite.shared.domain.BusinessRuleException if the patient has no valid DATA_PROCESSING consent
     *         or is blocked (MEJ-06)
     * @throws ec.rehabelite.shared.domain.ExternalServiceException if the email cannot be sent; nothing is created
     */
    @Transactional
    public AccountSummary create(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(PatientRegistrationService.PATIENT_NOT_FOUND));
        if (patient.getPortalUserId() != null) {
            throw new ConflictException(Patient.ALREADY_LINKED, Map.of(EXISTING_USER_ID, patient.getPortalUserId()));
        }
        // MEJ-06: the portal needs a valid DATA_PROCESSING consent; EMAIL_COMMUNICATIONS is not needed (the
        // activation link is an account email)
        consentService.checkCareAllowed(patient);
        if (!accounts.accountsUsingEmail(patient.getEmail()).isEmpty()) {
            throw new ConflictException(EMAIL_IN_USE, Map.of(PatientIdentityRules.FIELD, "email"));
        }

        Long userId = accounts.create(patient.getEmail(), patient.fullName(), Role.PATIENT);
        patient.linkPortalAccount(userId);
        auditLogService.record(AuditAction.UPDATE, PatientRegistrationService.AUDITED_ENTITY, patient.getId());
        accounts.sendInvitation(userId);
        log.info("Portal account created and linked patientId={} userId={}", patient.getId(), userId);
        return accounts.summary(userId).orElseThrow();
    }

    /** The patient's portal account, or empty when it has none. */
    @Transactional(readOnly = true)
    public Optional<AccountSummary> accountOf(Patient patient) {
        return accounts.summary(patient.getPortalUserId());
    }

}
