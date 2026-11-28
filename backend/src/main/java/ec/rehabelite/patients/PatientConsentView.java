package ec.rehabelite.patients;

import java.time.Instant;

/**
 * A consent type of a patient (MEJ-06): its status and the last consent of that type, if any.
 *
 * @param textVersion the version that was accepted; null when it was never given
 * @param currentTextVersion the version of the text in force
 * @param channel how it was given; null when it was never given
 * @param revocationChannel how it was revoked; null unless revoked
 */
public record PatientConsentView(ConsentType type, ConsentStatus status, String textVersion,
        String currentTextVersion, ConsentChannel channel, Instant grantedAt, Instant revokedAt,
        ConsentChannel revocationChannel) {

    /** Whether the consent is in force, for the text in force or an earlier one. */
    public boolean valid() {
        return status == ConsentStatus.CURRENT || status == ConsentStatus.PREVIOUS_VERSION;
    }

}
