package ec.rehabelite.auth.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUser;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.PortalAccessPolicy;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ExternalServiceException;
import ec.rehabelite.shared.domain.ForbiddenOperationException;
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
 * Account administration, used from the people registry ("Registro de Usuarios") and the records of its people:
 * activate or deactivate an account (HU-03) and resend its pending link (MEJ-01, MEJ-02). Accounts are created only
 * from the people registry, with the email and the name of a registered person
 * ({@link ec.rehabelite.auth.AccountProvisioning}); there is no list of accounts of its own since MEJ-02.
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    static final String AUDITED_ENTITY = "User";
    static final String USER_NOT_FOUND = "El usuario no existe.";
    static final String SELF_DEACTIVATION = "No puede desactivar su propia cuenta.";
    static final String NOT_PENDING = "Solo se puede reenviar el enlace a una cuenta habilitada que aún no se ha "
            + "activado o que tiene un cambio de correo por confirmar.";
    static final String NOT_MANAGED = "No puede administrar esa cuenta.";
    static final String LAST_ACTIVE_DIRECTOR = "Debe existir al menos una cuenta de dirección operativa activa.";

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private final UserRepository userRepository;
    private final AccountLinkSender linkSender;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final PortalAccessPolicy portalAccessPolicy;
    private final Clock clock;

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
        checkManagedByCaller(user);
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
     * @throws ForbiddenOperationException if the account is not managed by the caller's role (MEJ-05)
     * @throws BusinessRuleException if users try to deactivate their own account, or if it is the last active
     *         operations director (MEJ-05)
     */
    @Transactional
    public User changeStatus(Long userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        checkManagedByCaller(user);
        Long changedBy = currentUserProvider.currentUserId().orElse(null);
        if (!active && user.getId().equals(changedBy)) {
            throw new BusinessRuleException(SELF_DEACTIVATION);
        }
        if (user.isActive() == active) {
            return user;
        }
        if (!active && user.getRole() == Role.OPERATIONS_DIRECTOR) {
            checkAnotherActiveDirectorRemains(user);
        }
        if (active && user.getRole() == Role.PATIENT) {
            // MEJ-06: a portal account comes back only with a valid consent and a patient not blocked
            portalAccessPolicy.portalAccessDenial(user.getId()).ifPresent(reason -> {
                throw new BusinessRuleException(reason);
            });
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

    /**
     * MEJ-05: the reception manages only the patients' portal accounts and the operations director only the staff
     * accounts. Any other combination is a 403, audited as ACCESS_DENIED.
     */
    private void checkManagedByCaller(User user) {
        Role callerRole = currentUserProvider.currentUser().map(CurrentUser::role).orElse(null);
        boolean patientAccount = user.getRole() == Role.PATIENT;
        boolean allowed = (callerRole == Role.ADMINISTRATOR && patientAccount)
                || (callerRole == Role.OPERATIONS_DIRECTOR && !patientAccount);
        if (!allowed) {
            throw new ForbiddenOperationException(NOT_MANAGED);
        }
    }

    /**
     * MEJ-05: at least one operations director stays active (enabled and activated). The active directors are locked
     * first, so two directors deactivating each other at the same time run one after the other. A director that is
     * not active yet does not count: it cannot log in. V9 backs the rule with a trigger.
     */
    private void checkAnotherActiveDirectorRemains(User director) {
        List<User> activeDirectors = userRepository.lockActiveDirectors();
        boolean anotherRemains = activeDirectors.stream().anyMatch(other -> !other.getId().equals(director.getId()));
        if (!anotherRemains) {
            throw new BusinessRuleException(LAST_ACTIVE_DIRECTOR);
        }
    }

}
