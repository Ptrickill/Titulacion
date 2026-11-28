package ec.rehabelite.clinicalrecords.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResponsibleAssignmentRepository extends JpaRepository<ResponsibleAssignment, Long> {

    /** Every assignment of the treatment, the most recent first. */
    List<ResponsibleAssignment> findByTreatmentIdOrderByValidFromDescIdDesc(Long treatmentId);

    /** Every assignment of the treatments, the oldest first, in one query (MEJ-06: the export of the record). */
    @Query("SELECT a FROM ResponsibleAssignment a WHERE a.treatment.id IN :treatmentIds ORDER BY a.validFrom, a.id")
    List<ResponsibleAssignment> findAllByTreatmentIds(@Param("treatmentIds") Collection<Long> treatmentIds);

    /** The current assignment of each of the treatments, in one query (HU-09). */
    @Query("SELECT a FROM ResponsibleAssignment a WHERE a.treatment.id IN :treatmentIds AND a.validTo IS NULL")
    List<ResponsibleAssignment> findCurrentByTreatmentIds(@Param("treatmentIds") Collection<Long> treatmentIds);

}
