package ec.rehabelite.auth.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.PortalAccountCreated;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.AccountTokenPurpose;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account administration: list accounts, create them with a role (HU-02), activate or deactivate them (HU-03) and
 * send the activation link (MEJ-01): a new account has no password until its owner follows that link.
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    static final String AUDITED_ENTITY = "User";
    static final String DUPLICATE_EMAIL = "Ya existe un usuario registrado con ese correo electrónico.";
    static final String PATIENT_REQUIRED = "La cuenta de paciente debe vincularse a un paciente registrado.";
    static final String PATIENT_NOT_ALLOWED = "Solo las cuentas con rol Paciente se vinculan a un paciente.";
    static final String USER_NOT_FOUND = "El usuario no existe.";
    static final String SELF_DEACTIVATION = "No puede desactivar su propia cuenta.";
    static final String NOT_PENDING =
            "Solo se puede reenviar la invitación a una cuenta habilitada que aún no se ha activado.";
    static final String MAIL_FAILED =
            "No se pudo enviar el correo de activación. Intente nuevamente en unos minutos.";

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private final UserRepository userRepository;
    private final AccountTokenService tokenService;
    private final AccountMailer mailer;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAllByOrderByFullNameAsc();
    }

    /**
     * Creates an account pending activation, without a password, and emails its activation link (MEJ-01). For the
     * PATIENT role the patients module links the account to the patient in the same transaction (see
     * {@link PortalAccountCreated}). The email is sent last: if it cannot be sent, nothing is created.
     *
     * @throws BusinessRuleException if the email is taken or the patient rules are not met
     * @throws ExternalServiceException if the activation email cannot be sent
     */
    @Transactional
    public User create(CreateUserCommand command) {
        if (command.role() == Role.PATIENT && command.patientId() == null) {
            throw new BusinessRuleException(PATIENT_REQUIRED);
        }
        if (command.role() != Role.PATIENT && command.patientId() != null) {
            throw new BusinessRuleException(PATIENT_NOT_ALLOWED);
        }
        String email = User.normalizeEmail(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException(DUPLICATE_EMAIL);
        }

        Long createdBy = currentUserProvider.currentUserId().orElse(null);
        Instant now = clock.instant();
        User user = userRepository.save(User.invite(email, command.fullName().trim(), command.role(), createdBy, now));
        if (command.role() == Role.PATIENT) {
            events.publishEvent(new PortalAccountCreated(user.getId(), command.patientId()));
        }
        auditLogService.record(AuditAction.CREATE, AUDITED_ENTITY, user.getId());
        auditLogService.record(AuditAction.INVITATION_SENT, AUDITED_ENTITY, user.getId());
        sendActivation(user, now);
        log.info("User account created pending activation userId={} role={} createdBy={}", user.getId(),
                user.getRole(), createdBy);
        return user;
    }

    /**
     * Emails a new activation link to an account still pending activation (MEJ-01); the previous links stop
     * working. Audited as INVITATION_RESENT.
     *
     * @throws ResourceNotFoundException if the account does not exist
     * @throws BusinessRuleException if the account is already activated or is deactivated
     * @throws ExternalServiceException if the email cannot be sent (the previous links then keep working)
     */
    @Transactional
    public User resendInvitation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        if (!user.isActive() || !user.isPendingActivation()) {
            throw new BusinessRuleException(NOT_PENDING);
        }
        auditLogService.record(AuditAction.INVITATION_RESENT, AUDITED_ENTITY, user.getId());
        sendActivation(user, clock.instant());
        log.info("Activation link resent userId={}", user.getId());
        return user;
    }

    /** Issues the link and sends it; a delivery failure rolls the whole operation back. */
    private void sendActivation(User user, Instant now) {
        String token = tokenService.issue(user.getId(), AccountTokenPurpose.ACTIVATION, now);
        try {
            mailer.sendActivation(user, token);
        } catch (MailException ex) {
            throw new ExternalServiceException(MAIL_FAILED, ex);
        }
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
