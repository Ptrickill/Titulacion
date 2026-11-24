package ec.rehabelite.staff.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRecordRepository extends JpaRepository<StaffRecord, Long>, StaffSearch {

    Optional<StaffRecord> findByNationalId(String nationalId);

    /** The email is stored in lower case, so the argument must already be normalized. */
    Optional<StaffRecord> findByEmail(String email);

    Optional<StaffRecord> findByUserId(Long userId);

    List<StaffRecord> findByUserIdIn(Collection<Long> userIds);

    boolean existsByUserId(Long userId);

}
