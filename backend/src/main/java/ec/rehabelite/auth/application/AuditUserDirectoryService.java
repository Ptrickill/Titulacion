package ec.rehabelite.auth.application;

import ec.rehabelite.audit.AuditUser;
import ec.rehabelite.audit.AuditUserDirectory;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.auth.domain.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AuditUserDirectory} of the audit module (HU-18): auth already depends on audit, so it implements the
 * interface and audit never depends on auth.
 */
@Service
@RequiredArgsConstructor
class AuditUserDirectoryService implements AuditUserDirectory {

    private final UserRepository userRepository;
    private final StaffDirectory staffDirectory;

    @Override
    public Map<Long, String> fullNames(Collection<Long> userIds) {
        return staffDirectory.fullNames(userIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditUser> users() {
        return userRepository.findAll(Sort.by("fullName", "id")).stream()
                .map(user -> new AuditUser(user.getId(), user.getFullName()))
                .toList();
    }

}
