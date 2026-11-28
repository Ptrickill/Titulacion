package ec.rehabelite.patients.application;

import ec.rehabelite.auth.MailConsentPolicy;
import ec.rehabelite.auth.PortalAccessPolicy;
import ec.rehabelite.patients.ConsentType;
import ec.rehabelite.patients.PatientConsents;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.patients.domain.PatientConsentRepository;
import ec.rehabelite.patients.domain.PatientRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The consents of a patient applied to its portal account (MEJ-06), for the auth module, which declares both
 * policies: an account without a patient (staff) is never restricted.
 */
@Component
@RequiredArgsConstructor
class PatientAccountPolicies implements MailConsentPolicy, PortalAccessPolicy {

    private final PatientRepository patientRepository;
    private final PatientConsentRepository consentRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean allowsCommunications(Long userId) {
        return patientRepository.findByPortalUserId(userId)
                .map(patient -> !patient.isBlocked()
                        && consentRepository.existsOpen(patient.getId(), ConsentType.EMAIL_COMMUNICATIONS))
                .orElse(true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> portalAccessDenial(Long userId) {
        Optional<Patient> patient = patientRepository.findByPortalUserId(userId);
        if (patient.isEmpty()) {
            return Optional.empty();
        }
        if (patient.get().isBlocked()) {
            return Optional.of(PatientConsents.PATIENT_BLOCKED);
        }
        if (!consentRepository.existsOpen(patient.get().getId(), ConsentType.DATA_PROCESSING)) {
            return Optional.of(PatientConsents.NO_DATA_PROCESSING_CONSENT);
        }
        return Optional.empty();
    }

}
