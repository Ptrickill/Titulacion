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

    /** INACTIVE: deactivated; PENDING_ACTIVATION: enabled, email not verified; ACTIVE: enabled and verified. */
    @Query("""
            SELECT u.id FROM User u
            WHERE (:status = 'INACTIVE' AND u.active = false)
               OR (:status = 'PENDING_ACTIVATION' AND u.active = true AND u.emailVerifiedAt IS NULL)
               OR (:status = 'ACTIVE' AND u.active = true AND u.emailVerifiedAt IS NOT NULL)""")
    List<Long> findIdsByStatus(@Param("status") String status);

    /** Per role: how many accounts and how many of them are active (enabled and verified), in one statement. */
    @Query("""
            SELECT u.role AS role, count(u) AS accounts,
                   sum(CASE WHEN u.active = true AND u.emailVerifiedAt IS NOT NULL THEN 1 ELSE 0 END) AS active
            FROM User u GROUP BY u.role""")
    List<RoleCount> countByRole();

    interface RoleCount {

        Role getRole();

        long getAccounts();

        long getActive();

    }

    /** Active account whose session version matches the token's (HU-03 and MEJ-01). */
    boolean existsByIdAndActiveTrueAndSessionVersion(Long id, int sessionVersion);

    List<User> findByEmailEndingWith(String suffix);

    Optional<User> findByIdAndRoleAndActiveTrue(Long id, Role role);

    List<User> findByRoleAndActiveTrueOrderByFullNameAsc(Role role);

    List<User> findByRoleOrderByFullNameAscIdAsc(Role role);

}
