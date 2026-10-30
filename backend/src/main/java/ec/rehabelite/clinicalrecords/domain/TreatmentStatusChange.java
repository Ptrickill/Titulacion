package ec.rehabelite.clinicalrecords.domain;

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
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A status change of a treatment: previous and new status, the reason, who made it and when. Never modified. */
@Entity
@Table(name = "treatment_status_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TreatmentStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_id", nullable = false)
    private Treatment treatment;

    /** User id (auth module) of who made the change; referenced by id only. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 15)
    private TreatmentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 15)
    private TreatmentStatus newStatus;

    @Column(nullable = false)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    static TreatmentStatusChange of(Treatment treatment, TreatmentStatus previousStatus, TreatmentStatus newStatus,
            String reason, Long userId, Instant now) {
        TreatmentStatusChange change = new TreatmentStatusChange();
        change.treatment = treatment;
        change.previousStatus = previousStatus;
        change.newStatus = newStatus;
        change.reason = reason;
        change.userId = userId;
        change.changedAt = now;
        return change;
    }

}
