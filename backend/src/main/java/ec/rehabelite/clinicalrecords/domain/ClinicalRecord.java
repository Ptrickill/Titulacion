package ec.rehabelite.clinicalrecords.domain;

import ec.rehabelite.shared.domain.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The clinical record of a patient (one per patient): habits and medical history, plus its treatments. The
 * patient is referenced by id only; the patients module owns the personal data.
 */
@Entity
@Table(name = "clinical_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicalRecord {

    public static final String ACTIVE_TREATMENT_EXISTS = "El paciente ya tiene un tratamiento activo.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private String habits;

    @Column(name = "physical_activity", nullable = false)
    private String physicalActivity;

    @Column(nullable = false)
    private String allergies;

    @Column(name = "current_medication", nullable = false)
    private String currentMedication;

    @Column(name = "personal_history", nullable = false)
    private String personalHistory;

    @Column(name = "surgical_history", nullable = false)
    private String surgicalHistory;

    @Column(name = "family_history", nullable = false)
    private String familyHistory;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "clinicalRecord", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<Treatment> treatments = new ArrayList<>();

    public static ClinicalRecord create(Long patientId, ClinicalHistoryData history, Long createdBy, Instant now) {
        ClinicalRecord record = new ClinicalRecord();
        record.patientId = patientId;
        record.habits = history.habits();
        record.physicalActivity = history.physicalActivity();
        record.allergies = history.allergies();
        record.currentMedication = history.currentMedication();
        record.personalHistory = history.personalHistory();
        record.surgicalHistory = history.surgicalHistory();
        record.familyHistory = history.familyHistory();
        record.createdBy = createdBy;
        record.createdAt = now;
        return record;
    }

    /**
     * Opens a new ACTIVE treatment with the given physiotherapist as current responsible. A record has at most one
     * active treatment; HU-08 closes them (completed or abandoned).
     *
     * @throws BusinessRuleException if a treatment is still active, or the assessment values are out of range
     */
    public Treatment openTreatment(Icd10Diagnosis diagnosis, TreatmentData data, Long physiotherapistId,
            LocalDate startDate, Instant now) {
        if (hasActiveTreatment()) {
            throw new BusinessRuleException(ACTIVE_TREATMENT_EXISTS);
        }
        Treatment treatment = Treatment.open(this, diagnosis, data, physiotherapistId, startDate, now);
        treatments.add(treatment);
        return treatment;
    }

    public boolean hasActiveTreatment() {
        return treatments.stream().anyMatch(Treatment::isActive);
    }

}
