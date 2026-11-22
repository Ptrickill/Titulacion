package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.PortalAccountCreated;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.AccountStatus;
import ec.rehabelite.auth.domain.AccountTokenPurpose;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ExternalServiceException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

class UserManagementServiceTests {

    private static final Long ADMIN_ID = 1L;
    private static final Long NEW_USER_ID = 40L;
    private static final Instant NOW = Instant.parse("2026-10-29T14:00:00Z");
    private static final String TOKEN = "raw-activation-token";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountTokenService tokenService = mock(AccountTokenService.class);
    private final AccountMailer mailer = mock(AccountMailer.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final UserManagementService service = new UserManagementService(userRepository, tokenService, mailer,
            currentUserProvider, auditLogService, events, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(ADMIN_ID));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", NEW_USER_ID);
            return user;
        });
        when(tokenService.issue(any(), any(), any())).thenReturn(TOKEN);
    }

    @Test
    void createsAnAccountPendingActivationWithoutPasswordAndEmailsTheLink() {
        User user = service.create(new CreateUserCommand(" Lucia.Mora@RehabElite.test ", " Lucía Mora ",
                Role.PHYSIOTHERAPIST, null));

        assertThat(user.getEmail()).isEqualTo("lucia.mora@rehabelite.test");
        assertThat(user.getFullName()).isEqualTo("Lucía Mora");
        assertThat(user.isActive()).isTrue();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(user.status()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getCreatedBy()).isEqualTo(ADMIN_ID);
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        InOrder order = inOrder(auditLogService, tokenService, mailer);
        order.verify(auditLogService).record(AuditAction.CREATE, UserManagementService.AUDITED_ENTITY, NEW_USER_ID);
        order.verify(auditLogService)
                .record(AuditAction.INVITATION_SENT, UserManagementService.AUDITED_ENTITY, NEW_USER_ID);
        order.verify(tokenService).issue(NEW_USER_ID, AccountTokenPurpose.ACTIVATION, NOW);
        order.verify(mailer).sendActivation(user, TOKEN);
        verifyNoInteractions(events);
    }

    @Test
    void aMailFailureCancelsTheCreation() {
        doThrow(new MailSendException("down")).when(mailer).sendActivation(any(), any());

        assertThatThrownBy(() -> service.create(
                new CreateUserCommand("ana@rehabelite.test", "Ana", Role.ADMINISTRATOR, null)))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage(UserManagementService.MAIL_FAILED);
    }

    @Test
    void rejectsAnEmailThatIsAlreadyRegistered() {
        when(userRepository.existsByEmail("ana@rehabelite.test")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateUserCommand("ANA@rehabelite.test", "Ana", Role.ADMINISTRATOR, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(UserManagementService.DUPLICATE_EMAIL);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditLogService, tokenService, mailer);
    }

    @Test
    void patientAccountRequiresAPatient() {
        assertThatThrownBy(() -> service.create(
                new CreateUserCommand("rosa@rehabelite.test", "Rosa", Role.PATIENT, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(UserManagementService.PATIENT_REQUIRED);
        verify(userRepository, never()).save(any());
    }

    @Test
    void onlyPatientAccountsCanBeLinkedToAPatient() {
        assertThatThrownBy(() -> service.create(
                new CreateUserCommand("rosa@rehabelite.test", "Rosa", Role.PHYSIOTHERAPIST, 15L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(UserManagementService.PATIENT_NOT_ALLOWED);
    }

    @Test
    void patientAccountAsksThePatientsModuleToLinkIt() {
        User user = service.create(new CreateUserCommand("rosa@rehabelite.test", "Rosa Ortiz", Role.PATIENT, 15L));

        assertThat(user.getRole()).isEqualTo(Role.PATIENT);
        verify(events).publishEvent(new PortalAccountCreated(NEW_USER_ID, 15L));
        verify(auditLogService).record(AuditAction.CREATE, UserManagementService.AUDITED_ENTITY, NEW_USER_ID);
        verify(mailer).sendActivation(user, TOKEN);
    }

    @Test
    void resendsTheInvitationOfAPendingAccount() {
        User pending = pendingUser(NEW_USER_ID);

        service.resendInvitation(NEW_USER_ID);

        verify(auditLogService)
                .record(AuditAction.INVITATION_RESENT, UserManagementService.AUDITED_ENTITY, NEW_USER_ID);
        verify(tokenService).issue(NEW_USER_ID, AccountTokenPurpose.ACTIVATION, NOW);
        verify(mailer).sendActivation(pending, TOKEN);
    }

    @Test
    void onlyAnEnabledPendingAccountGetsTheInvitationAgain() {
        existingUser(NEW_USER_ID, Role.PHYSIOTHERAPIST);
        User deactivated = pendingUser(41L);
        deactivated.deactivate(ADMIN_ID, NOW);

        assertThatThrownBy(() -> service.resendInvitation(NEW_USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(UserManagementService.NOT_PENDING);
        assertThatThrownBy(() -> service.resendInvitation(41L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(UserManagementService.NOT_PENDING);
        verifyNoInteractions(auditLogService, tokenService, mailer);
    }

    @Test
    void resendingToAnUnknownAccountIsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resendInvitation(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(UserManagementService.USER_NOT_FOUND);
    }

    @Test
    void deactivatesAnAccountAndAuditsTheStatusChange() {
        User user = existingUser(NEW_USER_ID, Role.PHYSIOTHERAPIST);

        User result = service.changeStatus(NEW_USER_ID, false);

        assertThat(result.isActive()).isFalse();
        assertThat(result.getUpdatedBy()).isEqualTo(ADMIN_ID);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
        assertThat(result).isSameAs(user);
        verify(auditLogService).record(AuditAction.STATUS_CHANGE, UserManagementService.AUDITED_ENTITY, NEW_USER_ID);
    }

    @Test
    void reactivatesAnAccountWithoutFailedAttemptsOrLock() {
        User user = existingUser(NEW_USER_ID, Role.PATIENT);
        for (int attempt = 0; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            user.registerFailedAttempt(NOW);
        }
        user.deactivate(ADMIN_ID, NOW);

        User result = service.changeStatus(NEW_USER_ID, true);

        assertThat(result.isActive()).isTrue();
        assertThat(result.getFailedAttempts()).isZero();
        assertThat(result.isLocked(NOW)).isFalse();
        verify(auditLogService).record(AuditAction.STATUS_CHANGE, UserManagementService.AUDITED_ENTITY, NEW_USER_ID);
    }

    @Test
    void administratorsCannotDeactivateTheirOwnAccount() {
        User self = existingUser(ADMIN_ID, Role.ADMINISTRATOR);

        assertThatThrownBy(() -> service.changeStatus(ADMIN_ID, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(UserManagementService.SELF_DEACTIVATION);
        assertThat(self.isActive()).isTrue();
        verifyNoInteractions(auditLogService);
    }

    @Test
    void askingForTheCurrentStatusChangesNothing() {
        User user = existingUser(NEW_USER_ID, Role.OPERATIONS_DIRECTOR);

        User result = service.changeStatus(NEW_USER_ID, true);

        assertThat(result.isActive()).isTrue();
        assertThat(result.getUpdatedAt()).isNull();
        assertThat(result).isSameAs(user);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void unknownAccountIsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(99L, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(UserManagementService.USER_NOT_FOUND);
        verifyNoInteractions(auditLogService);
    }

    private User pendingUser(Long id) {
        User user = User.invite("pendiente" + id + "@rehabelite.test", "Pendiente " + id, Role.PHYSIOTHERAPIST,
                ADMIN_ID, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(user, "id", id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    private User existingUser(Long id, Role role) {
        User user = User.create("usuario" + id + "@rehabelite.test", "hash", "Usuario " + id, role, null,
                NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(user, "id", id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

}
