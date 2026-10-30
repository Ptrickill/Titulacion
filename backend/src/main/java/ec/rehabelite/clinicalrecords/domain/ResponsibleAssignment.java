package ec.rehabelite.clinicalrecords.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * A physiotherapist responsible for a treatment during a period. The current one has no end (validTo is null); a
 * partial unique index keeps at most one current assignment per treatment, and past ones are kept as history.
 */
@Entity
@Table(name = "responsible_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResponsibleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treatment_id", nullable = false)
    private Treatment treatment;

    /** User id of the physiotherapist (auth module); referenced by id only. */
    @Column(name = "physiotherapist_id", nullable = false)
    private Long physiotherapistId;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    static ResponsibleAssignment startingNow(Treatment treatment, Long physiotherapistId, Instant now) {
        ResponsibleAssignment assignment = new ResponsibleAssignment();
        assignment.treatment = treatment;
        assignment.physiotherapistId = physiotherapistId;
        assignment.validFrom = now;
        return assignment;
    }

    public boolean isCurrent() {
        return validTo == null;
    }

}
