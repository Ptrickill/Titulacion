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
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A treatment of a clinical record: diagnostic information, initial pain assessment and therapy plan. It starts
 * ACTIVE with the physiotherapist who opens it as the current responsible.
 */
@Entity
@Table(name = "treatments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Treatment {

    public static final int MIN_PAIN_SCORE = 0;
    public static final int MAX_PAIN_SCORE = 10;
    static final String PAIN_SCORE_OUT_OF_RANGE = "La EVA inicial debe estar entre 0 y 10.";
    static final String PLANNED_SESSIONS_NOT_POSITIVE = "Las sesiones planificadas deben ser mayores a 0.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinical_record_id", nullable = false)
    private ClinicalRecord clinicalRecord;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "diagnosis_code", nullable = false)
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

    @OneToMany(mappedBy = "treatment", cascade = CascadeType.PERSIST)
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

}
