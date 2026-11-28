package ec.rehabelite.patients.domain;

import ec.rehabelite.patients.ConsentChannel;
import ec.rehabelite.patients.ConsentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A consent given by a patient (MEJ-06): its type, the version of the text, how and when it was given and who
 * recorded it. It stays open until it is revoked, or replaced by the acceptance of a newer version of the same text;
 * a patient has at most one open consent per type (uq_patient_consents_current). Never deleted.
 */
@Entity
@Table(name = "patient_consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatientConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 30)
    private ConsentType type;

    @Column(name = "text_version", nullable = false, length = 30)
    private String textVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentChannel channel;

    /** The reception user who recorded a signed consent, or the patient's portal account. */
    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "replaced_at")
    private Instant replacedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_channel", length = 20)
    private ConsentChannel revocationChannel;

    public static PatientConsent grant(Long patientId, ConsentType type, String textVersion, ConsentChannel channel,
            Long grantedBy, Instant now) {
        if (channel == ConsentChannel.ERASURE) {
            throw new IllegalArgumentException("A consent is never given by an erasure request");
        }
        PatientConsent consent = new PatientConsent();
        consent.patientId = patientId;
        consent.type = type;
        consent.textVersion = textVersion;
        consent.channel = channel;
        consent.grantedBy = grantedBy;
        consent.grantedAt = now;
        return consent;
    }

    /** Neither revoked nor replaced: the consent in force for its type. */
    public boolean isOpen() {
        return revokedAt == null && replacedAt == null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** The patient accepted a newer version of the same text. */
    public void replace(Instant now) {
        replacedAt = now;
    }

    public void revoke(Long revokedBy, ConsentChannel channel, Instant now) {
        this.revokedAt = now;
        this.revokedBy = revokedBy;
        this.revocationChannel = channel;
    }

}
