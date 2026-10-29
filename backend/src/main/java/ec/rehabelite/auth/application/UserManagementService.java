package ec.rehabelite.auth.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.PortalAccountCreated;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.domain.BusinessRuleException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account administration (HU-02): list accounts and create them with a role.
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    static final String AUDITED_ENTITY = "User";
    static final String DUPLICATE_EMAIL = "Ya existe un usuario registrado con ese correo electrónico.";
    static final String PATIENT_REQUIRED = "La cuenta de paciente debe vincularse a un paciente registrado.";
    static final String PATIENT_NOT_ALLOWED = "Solo las cuentas con rol Paciente se vinculan a un paciente.";
    static final String PASSWORD_TOO_LONG = "La contraseña no puede superar los 72 bytes.";

    /** BCrypt ignores (Spring Security rejects) anything beyond 72 bytes. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAllByOrderByFullNameAsc();
    }

    /**
     * Creates an active account with a BCrypt password. For the PATIENT role the patients module links the
     * account to the patient in the same transaction (see {@link PortalAccountCreated}).
     *
     * @throws BusinessRuleException if the email is taken or the patient rules are not met
     */
    @Transactional
    public User create(CreateUserCommand command) {
        if (command.role() == Role.PATIENT && command.patientId() == null) {
            throw new BusinessRuleException(PATIENT_REQUIRED);
        }
        if (command.role() != Role.PATIENT && command.patientId() != null) {
            throw new BusinessRuleException(PATIENT_NOT_ALLOWED);
        }
        if (command.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BusinessRuleException(PASSWORD_TOO_LONG);
        }
        String email = User.normalizeEmail(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException(DUPLICATE_EMAIL);
        }

        Long createdBy = currentUserProvider.currentUserId().orElse(null);
        User user = userRepository.save(User.create(email, passwordEncoder.encode(command.password()),
                command.fullName().trim(), command.role(), createdBy, clock.instant()));
        if (command.role() == Role.PATIENT) {
            events.publishEvent(new PortalAccountCreated(user.getId(), command.patientId()));
        }
        auditLogService.record(AuditAction.CREATE, AUDITED_ENTITY, user.getId());
        log.info("User account created userId={} role={} createdBy={}", user.getId(), user.getRole(), createdBy);
        return user;
    }

}
