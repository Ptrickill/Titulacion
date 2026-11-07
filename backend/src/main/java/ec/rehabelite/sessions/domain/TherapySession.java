package ec.rehabelite.sessions.domain;

import ec.rehabelite.shared.domain.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

/**
 * A therapy session of a treatment (HU-10). The server assigns its sequence number and takes the authenticated
 * physiotherapist as responsible; neither changes afterwards. A session is never deleted: a correction replaces the
 * editable fields and Hibernate Envers keeps every version of the row in therapy_sessions_aud.
 * <p>
 * The treatment and the responsible belong to other modules, so they are referenced by id only.
 */
@Entity
@Table(name = "therapy_sessions")
@Audited
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TherapySession {

    public static final int MIN_PAIN_SCORE = 0;
    public static final int MAX_PAIN_SCORE = 10;
    static final String PAIN_SCORE_OUT_OF_RANGE = "La EVA debe estar entre 0 y 10.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "treatment_id", nullable = false, updatable = false)
    private Long treatmentId;

    @Column(name = "responsible_id", nullable = false, updatable = false)
    private Long responsibleId;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private int sequenceNumber;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "session_time", nullable = false)
    private LocalTime sessionTime;

    @Column(name = "session_type", nullable = false, length = 50)
    private String sessionType;

    @Column(name = "activities_performed", nullable = false)
    private String activitiesPerformed;

    /** Internal note for the physiotherapy team; never shown in the patient portal. */
    @Column(name = "clinical_observations", nullable = false)
    private String clinicalObservations;

    @Column(name = "patient_instructions")
    private String patientInstructions;

    /** Visual analogue scale (EVA), 0 to 10; smallint column. */
    @Column(name = "pain_score", nullable = false)
    private short painScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_status", nullable = false, length = 20)
    private ProgressStatus progressStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SessionStatus status;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * New session with the given sequence number, registered by (and under the responsibility of) the physiotherapist.
     *
     * @throws BusinessRuleException if the pain score is outside 0–10
     */
    public static TherapySession register(Long treatmentId, int sequenceNumber, Long physiotherapistId,
            TherapySessionData data, Instant now) {
        TherapySession session = new TherapySession();
        session.treatmentId = treatmentId;
        session.sequenceNumber = sequenceNumber;
        session.responsibleId = physiotherapistId;
        session.apply(data);
        session.createdBy = physiotherapistId;
        session.createdAt = now;
        return session;
    }

    /** Whether the user is the physiotherapist who registered the session, the only one who may correct it. */
    public boolean isRegisteredBy(Long userId) {
        return responsibleId.equals(userId);
    }

    /**
     * Replaces the editable fields; the sequence number and the responsible stay. Returns false, changing nothing,
     * when the data is the same as now.
     *
     * @throws BusinessRuleException if the pain score is outside 0–10
     */
    public boolean correct(TherapySessionData data, Long correctedBy, Instant now) {
        if (data().equals(data.normalized())) {
            return false;
        }
        apply(data);
        updatedBy = correctedBy;
        updatedAt = now;
        return true;
    }

    /** The editable fields as they are now. */
    public TherapySessionData data() {
        return new TherapySessionData(sessionDate, sessionTime, sessionType, activitiesPerformed,
                clinicalObservations, patientInstructions, painScore, progressStatus, status);
    }

    private void apply(TherapySessionData data) {
        if (data.painScore() < MIN_PAIN_SCORE || data.painScore() > MAX_PAIN_SCORE) {
            throw new BusinessRuleException(PAIN_SCORE_OUT_OF_RANGE);
        }
        TherapySessionData normalized = data.normalized();
        sessionDate = normalized.sessionDate();
        sessionTime = normalized.sessionTime();
        sessionType = normalized.sessionType();
        activitiesPerformed = normalized.activitiesPerformed();
        clinicalObservations = normalized.clinicalObservations();
        patientInstructions = normalized.patientInstructions();
        painScore = (short) normalized.painScore();
        progressStatus = normalized.progressStatus();
        status = normalized.status();
    }

}
