package ec.rehabelite.clinicalrecords.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentStatusChangeRepository extends JpaRepository<TreatmentStatusChange, Long> {

    /** Every status change of the treatment, the most recent first. */
    List<TreatmentStatusChange> findByTreatmentIdOrderByChangedAtDescIdDesc(Long treatmentId);

}
