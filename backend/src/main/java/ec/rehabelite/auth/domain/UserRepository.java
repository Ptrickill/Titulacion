package ec.rehabelite.auth.domain;

import ec.rehabelite.auth.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Emails are stored in lower case, so the argument must already be normalized. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    List<User> findAllByOrderByFullNameAsc();

    /** Active account whose session version matches the token's (HU-03 and MEJ-01). */
    boolean existsByIdAndActiveTrueAndSessionVersion(Long id, int sessionVersion);

    List<User> findByEmailEndingWith(String suffix);

    Optional<User> findByIdAndRoleAndActiveTrue(Long id, Role role);

    List<User> findByRoleAndActiveTrueOrderByFullNameAsc(Role role);

    List<User> findByRoleOrderByFullNameAscIdAsc(Role role);

}
