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

    boolean existsByIdAndActiveTrue(Long id);

    List<User> findByEmailEndingWith(String suffix);

}
