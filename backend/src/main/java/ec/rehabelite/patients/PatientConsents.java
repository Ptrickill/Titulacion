package ec.rehabelite.patients;

import java.util.List;

/**
 * Public API of the consents (MEJ-06) for other modules: the clinical modules check that a patient may receive care,
 * and the portal lets the patient see, accept and revoke them. The patient of the portal is always the one linked to
 * the authenticated account, never an id sent by the client.
 */
public interface PatientConsents {

    String NO_DATA_PROCESSING_CONSENT = "El paciente no tiene un consentimiento vigente para el tratamiento de sus "
            + "datos. Regístrelo en su ficha.";
    String PATIENT_BLOCKED = "Los datos del paciente están bloqueados por una solicitud de eliminación.";

    /**
     * Checks that the patient may receive new care: a new clinical record, treatment, session or appointment. Needs a
     * valid DATA_PROCESSING consent (of any version) and a patient not blocked by an erasure request.
     *
     * @throws ec.rehabelite.shared.domain.ResourceNotFoundException if the patient does not exist
     * @throws ec.rehabelite.shared.domain.BusinessRuleException with {@link #NO_DATA_PROCESSING_CONSENT} or
     *         {@link #PATIENT_BLOCKED}
     */
    void checkCareAllowed(Long patientId);

    /** The texts of the consents in force, DATA_PROCESSING first. */
    List<LegalText> consentTexts();

    /**
     * The consents of the patient linked to the portal account, one per type.
     *
     * @throws ec.rehabelite.shared.domain.ResourceNotFoundException if the account has no patient
     */
    List<PatientConsentView> ofPortalUser(Long userId);

    /**
     * The patient of the portal account accepts the text in force of a consent (channel PORTAL); an earlier version
     * is replaced. Audited as CREATE PatientConsent.
     *
     * @throws ec.rehabelite.shared.domain.BusinessRuleException if the version is not the one in force, the consent
     *         is already current or the patient is blocked
     */
    List<PatientConsentView> acceptFromPortal(Long userId, ConsentType type, String version);

    /**
     * The patient of the portal account revokes EMAIL_COMMUNICATIONS. DATA_PROCESSING cannot be revoked from the
     * portal (it would close the patient's own access): the patient asks the reception, as a request of the data
     * subject. Audited as STATUS_CHANGE PatientConsent.
     *
     * @throws ec.rehabelite.shared.domain.BusinessRuleException for DATA_PROCESSING or without a valid consent
     */
    List<PatientConsentView> revokeFromPortal(Long userId, ConsentType type);

}
