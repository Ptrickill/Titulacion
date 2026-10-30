package ec.rehabelite.clinicalrecords.domain;

import ec.rehabelite.shared.domain.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;

/**
 * A treatment of a clinical record: diagnostic information, initial pain assessment and therapy plan. It starts
 * ACTIVE with the physiotherapist who opens it as the current responsible; HU-08 changes its status and responsible,
 * keeping both histories. Hibernate Envers keeps every version of its row in treatments_aud (HU-18).
 */
@Entity
@Table(name = "treatments")
@Audited
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Treatment {

    public static final int MIN_PAIN_SCORE = 0;
    public static final int MAX_PAIN_SCORE = 10;
    static final String PAIN_SCORE_OUT_OF_RANGE = "La EVA inicial debe estar entre 0 y 10.";
    static final String PLANNED_SESSIONS_NOT_POSITIVE = "Las sesiones planificadas deben ser mayores a 0.";
    public static final String STATUS_TRANSITION_NOT_ALLOWED = "Transición de estado no permitida.";
    public static final String STATUS_CHANGE_REASON_REQUIRED = "El motivo del cambio de estado es obligatorio.";
    public static final String RESPONSIBLE_CHANGE_NEEDS_ACTIVE_TREATMENT =
            "Solo se puede cambiar el responsable de un tratamiento activo.";

    /**
     * Allowed status changes (HU-08): an active treatment is completed or abandoned, and an abandoned one can be
     * resumed. COMPLETED is final: if the patient comes back, a new treatment is opened.
     */
    private static final Map<TreatmentStatus, Set<TreatmentStatus>> ALLOWED_TRANSITIONS = Map.of(
            TreatmentStatus.ACTIVE, EnumSet.of(TreatmentStatus.COMPLETED, TreatmentStatus.ABANDONED),
            TreatmentStatus.ABANDONED, EnumSet.of(TreatmentStatus.ACTIVE),
            TreatmentStatus.COMPLETED, EnumSet.noneOf(TreatmentStatus.class));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The versions keep only the record id: the record itself is not versioned. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinical_record_id", nullable = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private ClinicalRecord clinicalRecord;

    /** The versions keep only the code: the catalog is not versioned. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "diagnosis_code", nullable = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Icd10Diagnosis diagnosis;

    @Column(name = "chief_complaint", nullable = false)
    private String chiefComplaint;

    @Column(name = "current_illness", nullable = false)
    private String currentIllness;

    @Column(name = "referring_physician", nullable = false, length = 150)
    private String referringPhysician;

    @Column(name = "complementary_tests", nullable = false)
    private String complementaryTests;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Column(name = "pain_type", nullable = false, length = 50)
    private String painType;

    @Column(name = "pain_location", nullable = false, length = 100)
    private String painLocation;

    /** Visual analogue scale (EVA), 0 to 10; smallint column. */
    @Column(name = "initial_pain_score", nullable = false)
    private short initialPainScore;

    @Column(name = "pain_frequency", nullable = false, length = 50)
    private String painFrequency;

    @Column(name = "session_frequency", nullable = false, length = 50)
    private String sessionFrequency;

    @Column(nullable = false)
    private String goal;

    @Column(name = "planned_sessions", nullable = false)
    private int plannedSessions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TreatmentStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Not versioned: responsible_assignments is already the history of responsibles. */
    @OneToMany(mappedBy = "treatment", cascade = CascadeType.PERSIST)
    @NotAudited
    private List<ResponsibleAssignment> responsibleAssignments = new ArrayList<>();

    /**
     * New ACTIVE treatment whose current responsible is the physiotherapist who opens it.
     *
     * @throws BusinessRuleException if the pain score is outside 0–10 or the planned sessions are not positive
     */
    static Treatment open(ClinicalRecord clinicalRecord, Icd10Diagnosis diagnosis, TreatmentData data,
            Long physiotherapistId, LocalDate startDate, Instant now) {
        if (data.initialPainScore() < MIN_PAIN_SCORE || data.initialPainScore() > MAX_PAIN_SCORE) {
            throw new BusinessRuleException(PAIN_SCORE_OUT_OF_RANGE);
        }
        if (data.plannedSessions() <= 0) {
            throw new BusinessRuleException(PLANNED_SESSIONS_NOT_POSITIVE);
        }
        Treatment treatment = new Treatment();
        treatment.clinicalRecord = clinicalRecord;
        treatment.diagnosis = diagnosis;
        treatment.chiefComplaint = data.chiefComplaint();
        treatment.currentIllness = data.currentIllness();
        treatment.referringPhysician = data.referringPhysician();
        treatment.complementaryTests = data.complementaryTests();
        treatment.severity = data.severity();
        treatment.painType = data.painType();
        treatment.painLocation = data.painLocation();
        treatment.initialPainScore = (short) data.initialPainScore();
        treatment.painFrequency = data.painFrequency();
        treatment.sessionFrequency = data.sessionFrequency();
        treatment.goal = data.goal();
        treatment.plannedSessions = data.plannedSessions();
        treatment.status = TreatmentStatus.ACTIVE;
        treatment.startDate = startDate;
        treatment.createdBy = physiotherapistId;
        treatment.createdAt = now;
        treatment.responsibleAssignments.add(ResponsibleAssignment.startingNow(treatment, physiotherapistId, now));
        return treatment;
    }

    public boolean isActive() {
        return status == TreatmentStatus.ACTIVE;
    }

    /** The responsible physiotherapist now (the assignment without an end). */
    public Optional<ResponsibleAssignment> currentResponsible() {
        return responsibleAssignments.stream().filter(ResponsibleAssignment::isCurrent).findFirst();
    }

    /** The statuses this treatment can move to now, in declaration order. */
    public List<TreatmentStatus> allowedNextStatuses() {
        return List.copyOf(ALLOWED_TRANSITIONS.get(status));
    }

    /**
     * Moves the treatment to a new status and returns the change to keep as history. Resuming an abandoned treatment
     * requires that no other treatment of the record is active.
     *
     * @throws BusinessRuleException if the transition is not allowed, the reason is blank or another treatment of
     *         the record is active
     */
    public TreatmentStatusChange changeStatus(TreatmentStatus newStatus, String reason, Long changedBy,
            Instant now) {
        if (!ALLOWED_TRANSITIONS.get(status).contains(newStatus)) {
            throw new BusinessRuleException(STATUS_TRANSITION_NOT_ALLOWED);
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException(STATUS_CHANGE_REASON_REQUIRED);
        }
        if (newStatus == TreatmentStatus.ACTIVE && clinicalRecord.hasActiveTreatment()) {
            throw new BusinessRuleException(ClinicalRecord.ACTIVE_TREATMENT_EXISTS);
        }
        TreatmentStatusChange change = TreatmentStatusChange.of(this, status, newStatus, reason.strip(), changedBy,
                now);
        status = newStatus;
        markUpdated(changedBy, now);
        return change;
    }

    /**
     * Hands the treatment over to another physiotherapist: the current assignment ends now and a new one starts.
     * Returns empty when the physiotherapist is already the current responsible (nothing changes).
     * <p>
     * The new assignment is returned but not added to {@link #responsibleAssignments}: the caller must flush the
     * ended assignment before persisting it, because the partial unique index allows a single current assignment and
     * Hibernate runs inserts before updates.
     *
     * @throws BusinessRuleException if the treatment is not active
     */
    public Optional<ResponsibleAssignment> reassignResponsible(Long physiotherapistId, Long changedBy, Instant now) {
        if (!isActive()) {
            throw new BusinessRuleException(RESPONSIBLE_CHANGE_NEEDS_ACTIVE_TREATMENT);
        }
        Optional<ResponsibleAssignment> current = currentResponsible();
        if (current.isPresent() && current.get().getPhysiotherapistId().equals(physiotherapistId)) {
            return Optional.empty();
        }
        current.ifPresent(assignment -> assignment.end(now));
        markUpdated(changedBy, now);
        return Optional.of(ResponsibleAssignment.startingNow(this, physiotherapistId, now));
    }

    private void markUpdated(Long updatedBy, Instant now) {
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

}
