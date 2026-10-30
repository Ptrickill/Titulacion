package ec.rehabelite.auth.application;

import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.auth.StaffMember;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link StaffDirectory} for the other modules, backed by the user repository. */
@Service
@RequiredArgsConstructor
class StaffDirectoryService implements StaffDirectory {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffMember> findActivePhysiotherapist(Long userId) {
        return userRepository.findByIdAndRoleAndActiveTrue(userId, Role.PHYSIOTHERAPIST)
                .map(StaffDirectoryService::toMember);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffMember> activePhysiotherapists() {
        return userRepository.findByRoleAndActiveTrueOrderByFullNameAsc(Role.PHYSIOTHERAPIST).stream()
                .map(StaffDirectoryService::toMember)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> fullNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private static StaffMember toMember(User user) {
        return new StaffMember(user.getId(), user.getFullName());
    }

}
