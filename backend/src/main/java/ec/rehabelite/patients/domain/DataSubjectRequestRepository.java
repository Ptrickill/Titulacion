package ec.rehabelite.patients.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, Long> {

    /** The requests of the patient, the most recently received first. */
    List<DataSubjectRequest> findByPatientIdOrderByReceivedOnDescIdDesc(Long patientId);

    /** The open requests of a type, the oldest first (ix_data_subject_requests_open). */
    @Query("""
            SELECT r FROM DataSubjectRequest r
            WHERE r.type = :type
              AND r.status IN (ec.rehabelite.patients.domain.DataRequestStatus.RECEIVED,
                               ec.rehabelite.patients.domain.DataRequestStatus.IN_PROGRESS)
            ORDER BY r.receivedOn, r.id""")
    List<DataSubjectRequest> findOpenByType(DataRequestType type);

}
