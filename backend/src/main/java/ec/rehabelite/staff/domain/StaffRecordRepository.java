package ec.rehabelite.staff.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StaffRecordRepository extends JpaRepository<StaffRecord, Long>, StaffSearch {

    Optional<StaffRecord> findByNationalId(String nationalId);

    /** The email is stored in lower case, so the argument must already be normalized. */
    Optional<StaffRecord> findByEmail(String email);

    Optional<StaffRecord> findByUserId(Long userId);

    List<StaffRecord> findByUserIdIn(Collection<Long> userIds);

    boolean existsByUserId(Long userId);

    /**
     * Locks the record until the transaction ends (MEJ-04): two replacements of the same schedule run one after the
     * other, so the second one deletes the slots that the first one saved.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StaffRecord s WHERE s.id = :id")
    Optional<StaffRecord> findForUpdate(Long id);

}
