package ec.rehabelite.patients.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.AccountProvisioning;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.patients.ConsentChannel;
import ec.rehabelite.patients.ConsentStatus;
import ec.rehabelite.patients.ConsentType;
import ec.rehabelite.patients.LegalText;
import ec.rehabelite.patients.PatientConsentView;
import ec.rehabelite.patients.PatientConsents;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.patients.domain.PatientConsent;
import ec.rehabelite.patients.domain.PatientConsentRepository;
import ec.rehabelite.patients.domain.PatientRepository;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consents of the patients (MEJ-06). The reception records the signed ones (in the registration and later in the
 * record) and revokes them; the patient accepts the texts in force and revokes the email communications in the portal.
 * Revoking DATA_PROCESSING deactivates the portal account and blocks new care; what was recorded is kept. Every change
 * locks the patient row, so two changes of the same patient go one after another.
 */
@Service
@RequiredArgsConstructor
public class ConsentService implements PatientConsents {

    static final String AUDITED_ENTITY = "PatientConsent";
    static final String OUTDATED_TEXT = "El texto del consentimiento cambió. Revíselo de nuevo con el paciente antes "
            + "de registrarlo.";
    static final String ALREADY_CURRENT = "El paciente ya tiene vigente ese consentimiento.";
    static final String NOTHING_TO_REVOKE = "El paciente no tiene vigente ese consentimiento.";
    static final String PORTAL_CANNOT_REVOKE_DATA_PROCESSING = "Para revocar el consentimiento del tratamiento de sus "
            + "datos, solicítelo en la recepción de la clínica.";
    static final String NO_LINKED_PATIENT = "La cuenta no tiene un paciente vinculado.";

    private static final Logger log = LoggerFactory.getLogger(ConsentService.class);

    private final PatientRepository patientRepository;
    private final PatientConsentRepository consentRepository;
    private final LegalTextCatalog legalTexts;
    private final AccountProvisioning accounts;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /**
     * The consents signed at the reception when the patient is registered (MEJ-06): DATA_PROCESSING always, and
     * EMAIL_COMMUNICATIONS when its version is given. Each version must be the one in force.
     *
     * @throws BusinessRuleException if a version is not the one in force
     */
    @Transactional
    public void grantAtRegistration(Patient patient, String dataProcessingVersion, String emailVersion) {
        checkVersion(ConsentType.DATA_PROCESSING, dataProcessingVersion);
        if (emailVersion != null) {
            checkVersion(ConsentType.EMAIL_COMMUNICATIONS, emailVersion);
        }
        Long userId = currentUserProvider.currentUserId().orElse(null);
        Instant now = clock.instant();
        save(PatientConsent.grant(patient.getId(), ConsentType.DATA_PROCESSING, dataProcessingVersion,
                ConsentChannel.PHYSICAL_SIGNATURE, userId, now));
        if (emailVersion != null) {
            save(PatientConsent.grant(patient.getId(), ConsentType.EMAIL_COMMUNICATIONS, emailVersion,
                    ConsentChannel.PHYSICAL_SIGNATURE, userId, now));
        }
    }

    /** The status of each consent type of the patient, DATA_PROCESSING first. */
    @Transactional(readOnly = true)
    public List<PatientConsentView> consentsOf(Long patientId) {
        findPatient(patientId);
        return views(consentRepository.findByPatientIdOrderByGrantedAtDescIdDesc(patientId));
    }

    /** Every consent of the patient, the most recent first. */
    @Transactional(readOnly = true)
    public List<PatientConsent> historyOf(Long patientId) {
        findPatient(patientId);
        return consentRepository.findByPatientIdOrderByGrantedAtDescIdDesc(patientId);
    }

    /** Whether the patient has no valid DATA_PROCESSING consent ("Consentimiento pendiente"). */
    @Transactional(readOnly = true)
    public boolean isDataProcessingPending(Long patientId) {
        return !consentRepository.existsOpen(patientId, ConsentType.DATA_PROCESSING);
    }

    /**
     * The reception records a consent signed on paper, for the text in force; an earlier version is replaced.
     * Audited as CREATE PatientConsent.
     *
     * @throws ResourceNotFoundException if the patient does not exist
     * @throws BusinessRuleException if the version is not the one in force, the consent is already current or the
     *         patient is blocked
     */
    @Transactional
    public List<PatientConsentView> grantFromRecord(Long patientId, ConsentType type, String version) {
        Patient patient = lockPatient(patientId);
        grant(patient, type, version, ConsentChannel.PHYSICAL_SIGNATURE);
        return consentsOf(patientId);
    }

    /**
     * The reception records the revocation of a consent. Revoking DATA_PROCESSING deactivates the portal account of
     * the patient (audited as STATUS_CHANGE User). Audited as STATUS_CHANGE PatientConsent.
     *
     * @throws ResourceNotFoundException if the patient does not exist
     * @throws BusinessRuleException if the consent is not valid now
     */
    @Transactional
    public List<PatientConsentView> revokeFromRecord(Long patientId, ConsentType type) {
        Patient patient = lockPatient(patientId);
        revoke(patient, type, ConsentChannel.PHYSICAL_SIGNATURE);
        return consentsOf(patientId);
    }

    /**
     * Revokes every open consent of a patient whose erasure request was accepted (channel ERASURE), without
     * deactivating the account: the erasure does that itself.
     */
    @Transactional
    public void revokeAllForErasure(Patient patient) {
        Long userId = currentUserProvider.currentUserId().orElse(null);
        Instant now = clock.instant();
        for (PatientConsent consent : consentRepository.findAllOpen(patient.getId())) {
            consent.revoke(userId, ConsentChannel.ERASURE, now);
            auditLogService.record(AuditAction.STATUS_CHANGE, AUDITED_ENTITY, consent.getId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void checkCareAllowed(Long patientId) {
        Patient patient = findPatient(patientId);
        checkCareAllowed(patient);
    }

    /** The same check on a patient already read. */
    void checkCareAllowed(Patient patient) {
        if (patient.isBlocked()) {
            throw new BusinessRuleException(PATIENT_BLOCKED);
        }
        if (!consentRepository.existsOpen(patient.getId(), ConsentType.DATA_PROCESSING)) {
            throw new BusinessRuleException(NO_DATA_PROCESSING_CONSENT);
        }
    }

    @Override
    public List<LegalText> consentTexts() {
        return legalTexts.consents();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientConsentView> ofPortalUser(Long userId) {
        return consentsOf(portalPatient(userId).getId());
    }

    @Override
    @Transactional
    public List<PatientConsentView> acceptFromPortal(Long userId, ConsentType type, String version) {
        Patient patient = lockPatient(portalPatient(userId).getId());
        grant(patient, type, version, ConsentChannel.PORTAL);
        return consentsOf(patient.getId());
    }

    @Override
    @Transactional
    public List<PatientConsentView> revokeFromPortal(Long userId, ConsentType type) {
        if (type == ConsentType.DATA_PROCESSING) {
            throw new BusinessRuleException(PORTAL_CANNOT_REVOKE_DATA_PROCESSING);
        }
        Patient patient = lockPatient(portalPatient(userId).getId());
        revoke(patient, type, ConsentChannel.PORTAL);
        return consentsOf(patient.getId());
    }

    private void grant(Patient patient, ConsentType type, String version, ConsentChannel channel) {
        if (patient.isBlocked()) {
            throw new BusinessRuleException(PATIENT_BLOCKED);
        }
        checkVersion(type, version);
        Instant now = clock.instant();
        consentRepository.findOpen(patient.getId(), type).ifPresent(open -> {
            if (open.getTextVersion().equals(version)) {
                throw new BusinessRuleException(ALREADY_CURRENT);
            }
            open.replace(now);
            // The partial unique index allows one open consent per type: close the earlier one first
            consentRepository.flush();
        });
        save(PatientConsent.grant(patient.getId(), type, version, channel,
                currentUserProvider.currentUserId().orElse(null), now));
    }

    private void revoke(Patient patient, ConsentType type, ConsentChannel channel) {
        PatientConsent open = consentRepository.findOpen(patient.getId(), type)
                .orElseThrow(() -> new BusinessRuleException(NOTHING_TO_REVOKE));
        Long userId = currentUserProvider.currentUserId().orElse(null);
        open.revoke(userId, channel, clock.instant());
        auditLogService.record(AuditAction.STATUS_CHANGE, AUDITED_ENTITY, open.getId());
        log.info("Consent revoked patientId={} type={} channel={} by={}", patient.getId(), type, channel, userId);
        if (type == ConsentType.DATA_PROCESSING && patient.getPortalUserId() != null) {
            accounts.deactivate(patient.getPortalUserId());
        }
    }

    private void save(PatientConsent consent) {
        consentRepository.save(consent);
        auditLogService.record(AuditAction.CREATE, AUDITED_ENTITY, consent.getId());
        log.info("Consent given patientId={} type={} channel={} consentId={}", consent.getPatientId(),
                consent.getType(), consent.getChannel(), consent.getId());
    }

    private void checkVersion(ConsentType type, String version) {
        if (!legalTexts.currentVersion(type).equals(version)) {
            throw new BusinessRuleException(OUTDATED_TEXT);
        }
    }

    private List<PatientConsentView> views(List<PatientConsent> history) {
        return Arrays.stream(ConsentType.values()).map(type -> view(type, history.stream()
                .filter(consent -> consent.getType() == type).findFirst().orElse(null))).toList();
    }

    private PatientConsentView view(ConsentType type, PatientConsent latest) {
        String current = legalTexts.currentVersion(type);
        if (latest == null) {
            return new PatientConsentView(type, ConsentStatus.PENDING, null, current, null, null, null, null);
        }
        ConsentStatus status;
        if (latest.isRevoked()) {
            status = ConsentStatus.REVOKED;
        } else if (latest.getTextVersion().equals(current)) {
            status = ConsentStatus.CURRENT;
        } else {
            status = ConsentStatus.PREVIOUS_VERSION;
        }
        return new PatientConsentView(type, status, latest.getTextVersion(), current, latest.getChannel(),
                latest.getGrantedAt(), latest.getRevokedAt(), latest.getRevocationChannel());
    }

    private Patient findPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(PatientRegistrationService.PATIENT_NOT_FOUND));
    }

    private Patient lockPatient(Long patientId) {
        return patientRepository.findForUpdate(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(PatientRegistrationService.PATIENT_NOT_FOUND));
    }

    private Patient portalPatient(Long userId) {
        return patientRepository.findByPortalUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(NO_LINKED_PATIENT));
    }

}
