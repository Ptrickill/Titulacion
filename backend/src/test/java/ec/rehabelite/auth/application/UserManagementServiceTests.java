package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUser;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ForbiddenOperationException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserManagementServiceTests {

    private static final Long ADMIN_ID = 1L;
    private static final Long USER_ID = 40L;
    private static final Instant NOW = Instant.parse("2026-10-29T14:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountLinkSender linkSender = mock(AccountLinkSender.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final UserManagementService service = new UserManagementService(userRepository, linkSender,
            currentUserProvider, auditLogService, Clock.fixed(NOW, ZoneOffset.UTC));

    /** The operations director acts by default (MEJ-05: staff accounts); the tests of patients act as the reception. */
    @BeforeEach
    void setUp() {
        actAs(Role.OPERATIONS_DIRECTOR);
    }

    @Test
    void resendsTheInvitationOfAPendingAccount() {
        User pending = pendingUser(USER_ID);

        service.resendInvitation(USER_ID);

        verify(auditLogService)
                .record(AuditAction.INVITATION_RESENT, UserManagementService.AUDITED_ENTITY, USER_ID);
        verify(linkSender).sendActivation(pending, NOW);
    }

    @Test
    void resendsTheConfirmationOfAPendingEmailChange() {
        User user = existingUser(USER_ID, Role.PHYSIOTHERAPIST);
        user.requestEmailChange("nuevo@rehabelite.test", ADMIN_ID, NOW);

        service.resendInvitation(USER_ID);

        verify(auditLogService)
                .record(AuditAction.INVITATION_RESENT, UserManagementService.AUDITED_ENTITY, USER_ID);
        verify(linkSender).sendEmailChange(user, NOW);
    }

    @Test
    void onlyAnEnabledAccountThatWaitsForItsOwnerGetsALinkAgain() {
        existingUser(USER_ID, Role.PHYSIOTHERAPIST);
        User deactivated = pendingUser(41L);
        deactivated.deactivate(ADMIN_ID, NOW);

        assertThatThrownBy(() -> service.resendInvitation(USER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(UserManagementService.NOT_PENDING);
        assertThatThrownBy(() -> service.resendInvitation(41L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(UserManagementService.NOT_PENDING);
        verifyNoInteractions(auditLogService, linkSender);
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
        User user = existingUser(USER_ID, Role.PHYSIOTHERAPIST);

        User result = service.changeStatus(USER_ID, false);

        assertThat(result.isActive()).isFalse();
        assertThat(result.getUpdatedBy()).isEqualTo(ADMIN_ID);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
        assertThat(result).isSameAs(user);
        verify(auditLogService).record(AuditAction.STATUS_CHANGE, UserManagementService.AUDITED_ENTITY, USER_ID);
    }

    @Test
    void reactivatesAnAccountWithoutFailedAttemptsOrLock() {
        actAs(Role.ADMINISTRATOR);
        User user = existingUser(USER_ID, Role.PATIENT);
        for (int attempt = 0; attempt < User.MAX_FAILED_ATTEMPTS; attempt++) {
            user.registerFailedAttempt(NOW);
        }
        user.deactivate(ADMIN_ID, NOW);

        User result = service.changeStatus(USER_ID, true);

        assertThat(result.isActive()).isTrue();
        assertThat(result.getFailedAttempts()).isZero();
        assertThat(result.isLocked(NOW)).isFalse();
        verify(auditLogService).record(AuditAction.STATUS_CHANGE, UserManagementService.AUDITED_ENTITY, USER_ID);
    }

    @Test
    void directorsCannotDeactivateTheirOwnAccount() {
        User self = existingUser(ADMIN_ID, Role.OPERATIONS_DIRECTOR);

        assertThatThrownBy(() -> service.changeStatus(ADMIN_ID, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(UserManagementService.SELF_DEACTIVATION);
        assertThat(self.isActive()).isTrue();
        verifyNoInteractions(auditLogService);
    }

    @Test
    void askingForTheCurrentStatusChangesNothing() {
        User user = existingUser(USER_ID, Role.OPERATIONS_DIRECTOR);
        when(userRepository.lockActiveDirectors()).thenReturn(List.of(user));

        User result = service.changeStatus(USER_ID, true);

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

    @Test
    void theReceptionManagesOnlyPatientAccountsAndTheDirectorOnlyStaffAccounts() {
        existingUser(USER_ID, Role.PHYSIOTHERAPIST);
        existingUser(41L, Role.PATIENT);

        actAs(Role.ADMINISTRATOR);
        assertThatThrownBy(() -> service.changeStatus(USER_ID, false))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage(UserManagementService.NOT_MANAGED);
        assertThatThrownBy(() -> service.resendInvitation(USER_ID))
                .isInstanceOf(ForbiddenOperationException.class);

        actAs(Role.OPERATIONS_DIRECTOR);
        assertThatThrownBy(() -> service.changeStatus(41L, false))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage(UserManagementService.NOT_MANAGED);
        assertThatThrownBy(() -> service.resendInvitation(41L))
                .isInstanceOf(ForbiddenOperationException.class);

        for (Role role : List.of(Role.PHYSIOTHERAPIST, Role.PATIENT)) {
            actAs(role);
            assertThatThrownBy(() -> service.changeStatus(41L, false))
                    .isInstanceOf(ForbiddenOperationException.class);
        }
        verifyNoInteractions(auditLogService, linkSender);
    }

    @Test
    void theLastActiveOperationsDirectorIsNeverDeactivated() {
        User lastDirector = existingUser(USER_ID, Role.OPERATIONS_DIRECTOR);
        when(userRepository.lockActiveDirectors()).thenReturn(List.of(lastDirector));

        assertThatThrownBy(() -> service.changeStatus(USER_ID, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(UserManagementService.LAST_ACTIVE_DIRECTOR);
        assertThat(lastDirector.isActive()).isTrue();
        verifyNoInteractions(auditLogService);
    }

    @Test
    void aDirectorIsDeactivatedWhileAnotherOneStaysActive() {
        User director = existingUser(USER_ID, Role.OPERATIONS_DIRECTOR);
        User other = existingUser(42L, Role.OPERATIONS_DIRECTOR);
        when(userRepository.lockActiveDirectors()).thenReturn(List.of(director, other));

        assertThat(service.changeStatus(USER_ID, false).isActive()).isFalse();
        verify(userRepository).lockActiveDirectors();
    }

    private void actAs(Role role) {
        when(currentUserProvider.currentUser())
                .thenReturn(Optional.of(new CurrentUser(ADMIN_ID, "actor@rehabelite.test", "Actor", role)));
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(ADMIN_ID));
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
