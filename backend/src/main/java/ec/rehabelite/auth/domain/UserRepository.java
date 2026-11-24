package ec.rehabelite.auth.domain;

import ec.rehabelite.auth.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Emails are stored in lower case, so the argument must already be normalized. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Accounts whose email or pending new email (MEJ-02) is the given one; both arguments already normalized. */
    List<User> findByEmailOrPendingEmail(String email, String pendingEmail);

    @Query("SELECT u.id FROM User u WHERE u.role = :role")
    List<Long> findIdsByRole(@Param("role") Role role);

    boolean existsByRole(Role role);

    List<User> findAllByOrderByFullNameAsc();

    /** Active account whose session version matches the token's (HU-03 and MEJ-01). */
    boolean existsByIdAndActiveTrueAndSessionVersion(Long id, int sessionVersion);

    List<User> findByEmailEndingWith(String suffix);

    Optional<User> findByIdAndRoleAndActiveTrue(Long id, Role role);

    List<User> findByRoleAndActiveTrueOrderByFullNameAsc(Role role);

    List<User> findByRoleOrderByFullNameAscIdAsc(Role role);

}
