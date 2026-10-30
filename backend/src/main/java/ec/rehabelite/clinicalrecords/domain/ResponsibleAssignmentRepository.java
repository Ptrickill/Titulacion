package ec.rehabelite.clinicalrecords.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponsibleAssignmentRepository extends JpaRepository<ResponsibleAssignment, Long> {

    /** Every assignment of the treatment, the most recent first. */
    List<ResponsibleAssignment> findByTreatmentIdOrderByValidFromDescIdDesc(Long treatmentId);

}
