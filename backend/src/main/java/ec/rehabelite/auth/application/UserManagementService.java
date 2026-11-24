package ec.rehabelite.auth.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ExternalServiceException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account administration ("Gestión de Usuarios"): list accounts, activate or deactivate them (HU-03) and resend
 * their pending link (MEJ-01, MEJ-02). Since MEJ-02 accounts are created only from the people registry, with the
 * email and the name of a registered person ({@link ec.rehabelite.auth.AccountProvisioning}).
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    static final String AUDITED_ENTITY = "User";
    static final String USER_NOT_FOUND = "El usuario no existe.";
    static final String SELF_DEACTIVATION = "No puede desactivar su propia cuenta.";
    static final String NOT_PENDING = "Solo se puede reenviar el enlace a una cuenta habilitada que aún no se ha "
            + "activado o que tiene un cambio de correo por confirmar.";

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private final UserRepository userRepository;
    private final AccountLinkSender linkSender;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAllByOrderByFullNameAsc();
    }

    /**
     * Emails a new link to an enabled account that is waiting for its owner: the activation link while the account
     * is pending activation (MEJ-01), or the link to confirm the new email while a change is pending (MEJ-02). The
     * previous links of that kind stop working. Audited as INVITATION_RESENT.
     *
     * @throws ResourceNotFoundException if the account does not exist
     * @throws BusinessRuleException if the account is deactivated or nothing waits for its owner
     * @throws ExternalServiceException if the email cannot be sent (the previous links then keep working)
     */
    @Transactional
    public User resendInvitation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        if (!user.isActive() || (!user.isPendingActivation() && user.getPendingEmail() == null)) {
            throw new BusinessRuleException(NOT_PENDING);
        }
        auditLogService.record(AuditAction.INVITATION_RESENT, AUDITED_ENTITY, user.getId());
        Instant now = clock.instant();
        if (user.isPendingActivation()) {
            linkSender.sendActivation(user, now);
            log.info("Activation link resent userId={}", user.getId());
        } else {
            linkSender.sendEmailChange(user, now);
            log.info("Email confirmation link resent userId={}", user.getId());
        }
        return user;
    }

    /**
     * Activates or deactivates an account (HU-03). A deactivated account cannot log in and its issued tokens stop
     * working (see {@link ActiveAccountTokenValidator}). Asking for the status the account already has changes
     * nothing and is not audited.
     *
     * @throws ResourceNotFoundException if the account does not exist
     * @throws BusinessRuleException if administrators try to deactivate their own account
     */
    @Transactional
    public User changeStatus(Long userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        Long changedBy = currentUserProvider.currentUserId().orElse(null);
        if (!active && user.getId().equals(changedBy)) {
            throw new BusinessRuleException(SELF_DEACTIVATION);
        }
        if (user.isActive() == active) {
            return user;
        }

        if (active) {
            user.activate(changedBy, clock.instant());
        } else {
            user.deactivate(changedBy, clock.instant());
        }
        auditLogService.record(AuditAction.STATUS_CHANGE, AUDITED_ENTITY, user.getId());
        log.info("User account status changed userId={} active={} changedBy={}", user.getId(), active, changedBy);
        return user;
    }

}
