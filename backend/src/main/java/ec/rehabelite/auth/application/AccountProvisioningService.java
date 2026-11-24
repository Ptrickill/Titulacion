package ec.rehabelite.auth.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.AccountProvisioning;
import ec.rehabelite.auth.AccountSummary;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.AccountTokenPurpose;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ConflictException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link AccountProvisioning} for the people registry (MEJ-02). Never logs nor audits an email address. */
@Service
@RequiredArgsConstructor
@Transactional
class AccountProvisioningService implements AccountProvisioning {

    static final String AUDITED_ENTITY = "User";
    static final String EMAIL_IN_USE = "Ya existe una cuenta con ese correo electrónico.";
    static final String INACTIVE_EMAIL_CHANGE =
            "La cuenta de esta persona está desactivada. Reactívela antes de cambiar el correo.";

    private static final Logger log = LoggerFactory.getLogger(AccountProvisioningService.class);

    private final UserRepository userRepository;
    private final AccountTokenService tokenService;
    private final AccountLinkSender linkSender;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    public Long create(String email, String fullName, Role role) {
        String normalized = User.normalizeEmail(email);
        if (!userRepository.findByEmailOrPendingEmail(normalized, normalized).isEmpty()) {
            throw new ConflictException(EMAIL_IN_USE, Map.of());
        }
        Long createdBy = currentUserProvider.currentUserId().orElse(null);
        User user = userRepository.save(User.invite(normalized, fullName, role, createdBy, clock.instant()));
        auditLogService.record(AuditAction.CREATE, AUDITED_ENTITY, user.getId());
        log.info("User account created pending activation userId={} role={} createdBy={}", user.getId(), role,
                createdBy);
        return user.getId();
    }

    @Override
    public void sendInvitation(Long userId) {
        User user = find(userId);
        linkSender.sendActivation(user, clock.instant());
        auditLogService.record(AuditAction.INVITATION_SENT, AUDITED_ENTITY, user.getId());
        log.info("Activation link sent userId={}", user.getId());
    }

    @Override
    public void rename(Long userId, String fullName) {
        User user = find(userId);
        if (user.rename(fullName, currentUserProvider.currentUserId().orElse(null), clock.instant())) {
            log.info("Account name synchronized with its person userId={}", user.getId());
        }
    }

    @Override
    public void changeEmail(Long userId, String newEmail) {
        User user = find(userId);
        String email = User.normalizeEmail(newEmail);
        Long changedBy = currentUserProvider.currentUserId().orElse(null);
        Instant now = clock.instant();

        if (email.equals(user.getEmail())) {
            if (user.getPendingEmail() != null) {
                user.cancelEmailChange(changedBy, now);
                tokenService.revoke(user.getId(), AccountTokenPurpose.EMAIL_CHANGE, now);
                auditLogService.record(AuditAction.EMAIL_CHANGE, AUDITED_ENTITY, user.getId());
                log.info("Pending email change cancelled userId={} changedBy={}", user.getId(), changedBy);
            }
            return;
        }
        if (email.equals(user.getPendingEmail())) {
            return;
        }
        if (!user.isActive()) {
            throw new BusinessRuleException(INACTIVE_EMAIL_CHANGE);
        }
        boolean takenByAnother = userRepository.findByEmailOrPendingEmail(email, email).stream()
                .anyMatch(other -> !other.getId().equals(user.getId()));
        if (takenByAnother) {
            throw new ConflictException(EMAIL_IN_USE, Map.of());
        }

        auditLogService.record(AuditAction.EMAIL_CHANGE, AUDITED_ENTITY, user.getId());
        if (user.isPendingActivation()) {
            user.changeEmailBeforeActivation(email, changedBy, now);
            linkSender.sendActivation(user, now);
            log.info("Email of a pending account changed and activation link resent userId={} changedBy={}",
                    user.getId(), changedBy);
            return;
        }
        String previousEmail = user.getEmail();
        user.requestEmailChange(email, changedBy, now);
        linkSender.sendEmailChange(user, now);
        linkSender.sendEmailChangeNoticeAfterCommit(user.getId(), user.getFullName(), previousEmail);
        log.info("Email change requested, waiting for confirmation userId={} changedBy={}", user.getId(), changedBy);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountSummary> summary(Long userId) {
        return userId == null ? Optional.empty() : userRepository.findById(userId).map(User::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, AccountSummary> summaries(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .map(User::summary)
                .collect(Collectors.toMap(AccountSummary::userId, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountSummary> accountsUsingEmail(String email) {
        String normalized = User.normalizeEmail(email);
        return userRepository.findByEmailOrPendingEmail(normalized, normalized).stream().map(User::summary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> idsWithRole(Role role) {
        return userRepository.findIdsByRole(role);
    }

    private User find(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(UserManagementService.USER_NOT_FOUND));
    }

}
