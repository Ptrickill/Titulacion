package ec.rehabelite.clinicalrecords.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TreatmentStatusChangeRepository extends JpaRepository<TreatmentStatusChange, Long> {

    /** Every status change of the treatment, the most recent first. */
    List<TreatmentStatusChange> findByTreatmentIdOrderByChangedAtDescIdDesc(Long treatmentId);

    /** Every status change of the treatments, the oldest first, in one query (MEJ-06: the export of the record). */
    @Query("SELECT c FROM TreatmentStatusChange c WHERE c.treatment.id IN :treatmentIds ORDER BY c.changedAt, c.id")
    List<TreatmentStatusChange> findAllByTreatmentIds(@Param("treatmentIds") Collection<Long> treatmentIds);

}
