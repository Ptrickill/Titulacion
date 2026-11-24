package ec.rehabelite.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.AccountStatus;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.domain.AccountTokenPurpose;
import ec.rehabelite.auth.domain.User;
import ec.rehabelite.auth.domain.UserRepository;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** MEJ-02: accounts are created and changed only through a registered person. */
class AccountProvisioningServiceTests {

    private static final Long ADMIN_ID = 1L;
    private static final Long USER_ID = 40L;
    private static final Instant NOW = Instant.parse("2026-11-23T14:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountTokenService tokenService = mock(AccountTokenService.class);
    private final AccountLinkSender linkSender = mock(AccountLinkSender.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final AccountProvisioningService service = new AccountProvisioningService(userRepository, tokenService,
            linkSender, currentUserProvider, auditLogService, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(ADMIN_ID));
        when(userRepository.findByEmailOrPendingEmail(anyString(), anyString())).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", USER_ID);
            return user;
        });
    }

    @Test
    void createsAnAccountPendingActivationWithTheGivenNameAndWithoutSendingYet() {
        Long id = service.create(" Lucia.Mora@RehabElite.test ", "Lucía Mora", Role.PHYSIOTHERAPIST);

        assertThat(id).isEqualTo(USER_ID);
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getEmail().equals("lucia.mora@rehabelite.test") && user.getFullName().equals("Lucía Mora")
                        && user.getPasswordHash() == null && user.status() == AccountStatus.PENDING_ACTIVATION
                        && ADMIN_ID.equals(user.getCreatedBy())));
        verify(auditLogService).record(AuditAction.CREATE, AccountProvisioningService.AUDITED_ENTITY, USER_ID);
        verifyNoInteractions(linkSender);
    }

    @Test
    void anEmailUsedByAnotherAccountAlsoAsPendingIsAConflict() {
        User other = user(7L, "otra@rehabelite.test");
        when(userRepository.findByEmailOrPendingEmail("ana@rehabelite.test", "ana@rehabelite.test"))
                .thenReturn(List.of(other));

        assertThatThrownBy(() -> service.create("ANA@rehabelite.test", "Ana Paz", Role.ADMINISTRATOR))
                .isInstanceOf(ConflictException.class)
                .hasMessage(AccountProvisioningService.EMAIL_IN_USE);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void sendsTheInvitationAndAuditsIt() {
        User user = pending(USER_ID, "lucia@rehabelite.test");

        service.sendInvitation(USER_ID);

        verify(linkSender).sendActivation(user, NOW);
        verify(auditLogService).record(AuditAction.INVITATION_SENT, AccountProvisioningService.AUDITED_ENTITY,
                USER_ID);
    }

    @Test
    void renamesTheAccountOnlyWhenTheNameChanges() {
        User user = user(USER_ID, "lucia@rehabelite.test");

        service.rename(USER_ID, "Usuario " + USER_ID);
        assertThat(user.getUpdatedAt()).isNull();

        service.rename(USER_ID, "Lucía Fernanda Mora Paz");
        assertThat(user.getFullName()).isEqualTo("Lucía Fernanda Mora Paz");
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void aPendingAccountTakesTheNewEmailAndGetsANewActivationLink() {
        User user = pending(USER_ID, "antes@rehabelite.test");

        service.changeEmail(USER_ID, "Nuevo@RehabElite.test");

        assertThat(user.getEmail()).isEqualTo("nuevo@rehabelite.test");
        assertThat(user.getPendingEmail()).isNull();
        verify(auditLogService).record(AuditAction.EMAIL_CHANGE, AccountProvisioningService.AUDITED_ENTITY, USER_ID);
        verify(linkSender).sendActivation(user, NOW);
        verify(linkSender, never()).sendEmailChangeNoticeAfterCommit(any(), any(), any());
    }

    @Test
    void anActivatedAccountKeepsItsEmailUntilTheNewOneIsConfirmed() {
        User user = user(USER_ID, "antes@rehabelite.test");

        service.changeEmail(USER_ID, "nuevo@rehabelite.test");

        assertThat(user.getEmail()).isEqualTo("antes@rehabelite.test");
        assertThat(user.getPendingEmail()).isEqualTo("nuevo@rehabelite.test");
        assertThat(user.summary().emailChangePending()).isTrue();
        verify(auditLogService).record(AuditAction.EMAIL_CHANGE, AccountProvisioningService.AUDITED_ENTITY, USER_ID);
        verify(linkSender).sendEmailChange(user, NOW);
        verify(linkSender).sendEmailChangeNoticeAfterCommit(USER_ID, user.getFullName(), "antes@rehabelite.test");
    }

    @Test
    void goingBackToTheCurrentEmailCancelsThePendingChange() {
        User user = user(USER_ID, "antes@rehabelite.test");
        user.requestEmailChange("nuevo@rehabelite.test", ADMIN_ID, NOW);

        service.changeEmail(USER_ID, "antes@rehabelite.test");

        assertThat(user.getPendingEmail()).isNull();
        verify(tokenService).revoke(USER_ID, AccountTokenPurpose.EMAIL_CHANGE, NOW);
        verifyNoInteractions(linkSender);
    }

    @Test
    void theSameEmailChangesNothing() {
        user(USER_ID, "antes@rehabelite.test");

        service.changeEmail(USER_ID, "ANTES@rehabelite.test");

        verifyNoInteractions(auditLogService, linkSender, tokenService);
    }

    @Test
    void aDeactivatedAccountCannotChangeItsEmail() {
        User user = user(USER_ID, "antes@rehabelite.test");
        user.deactivate(ADMIN_ID, NOW);

        assertThatThrownBy(() -> service.changeEmail(USER_ID, "nuevo@rehabelite.test"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(AccountProvisioningService.INACTIVE_EMAIL_CHANGE);
        verifyNoInteractions(auditLogService, linkSender);
    }

    @Test
    void anEmailOfAnotherAccountIsAConflict() {
        user(USER_ID, "antes@rehabelite.test");
        User other = user(8L, "x@rehabelite.test");
        when(userRepository.findByEmailOrPendingEmail("otra@rehabelite.test", "otra@rehabelite.test"))
                .thenReturn(List.of(other));

        assertThatThrownBy(() -> service.changeEmail(USER_ID, "otra@rehabelite.test"))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(auditLogService, linkSender);
    }

    private User user(Long id, String email) {
        User user = User.create(email, "hash", "Usuario " + id, Role.PHYSIOTHERAPIST, null, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(user, "id", id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    private User pending(Long id, String email) {
        User user = User.invite(email, "Usuario " + id, Role.PHYSIOTHERAPIST, ADMIN_ID, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(user, "id", id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

}
