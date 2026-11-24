package ec.rehabelite.patients.application;

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
import ec.rehabelite.auth.AccountProvisioning;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.patients.domain.PatientRepository;
import ec.rehabelite.patients.domain.PersonalData;
import ec.rehabelite.shared.domain.PostalAddress;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import ec.rehabelite.shared.domain.Sex;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class PatientManagementServiceTests {

    private static final Long ADMIN_ID = 1L;
    private static final Long PATIENT_ID = 25L;
    private static final Long PORTAL_USER_ID = 31L;
    private static final Instant NOW = Instant.parse("2026-10-30T15:00:00Z");
    private static final LocalDate ADMISSION = LocalDate.of(2026, 10, 1);

    private final PatientRepository patientRepository = mock(PatientRepository.class);
    private final PatientIdentityRules identityRules = mock(PatientIdentityRules.class);
    private final AccountProvisioning accounts = mock(AccountProvisioning.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final PatientManagementService service = new PatientManagementService(patientRepository, identityRules,
            accounts, currentUserProvider, auditLogService, Clock.fixed(NOW, ZoneOffset.UTC));

    private Patient patient;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(ADMIN_ID));
        patient = Patient.register("1710034065", ADMISSION, personalData("0987654321", "juan.perez@correo.ec"), 2L,
                NOW.minusSeconds(86_400));
        ReflectionTestUtils.setField(patient, "id", PATIENT_ID);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
    }

    @Test
    void updatesThePatientAndAuditsIt() {
        Patient result = service.update(PATIENT_ID, ADMISSION, personalData("0991112233", "juan.perez@correo.ec"));

        assertThat(result.getPhone()).isEqualTo("0991112233");
        assertThat(result.getUpdatedBy()).isEqualTo(ADMIN_ID);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
        verify(auditLogService).record(AuditAction.UPDATE, PatientRegistrationService.AUDITED_ENTITY, PATIENT_ID);
        verify(identityRules, never()).checkEmail(anyString(), any());
        verifyNoInteractions(accounts);
    }

    @Test
    void sameDataIsNotAudited() {
        Patient result = service.update(PATIENT_ID, ADMISSION, personalData("0987654321", "juan.perez@correo.ec"));

        assertThat(result.getUpdatedAt()).isNull();
        verifyNoInteractions(auditLogService);
    }

    @Test
    void aNewEmailIsCheckedBeforeSaving() {
        service.update(PATIENT_ID, ADMISSION, personalData("0987654321", "Juan.Nuevo@correo.ec"));

        verify(identityRules).checkEmail("juan.nuevo@correo.ec", null);
        assertThat(patient.getEmail()).isEqualTo("juan.nuevo@correo.ec");
    }

    @Test
    void withAPortalAccountTheAccountFollowsTheNamesAndTheEmail() {
        patient.linkPortalAccount(PORTAL_USER_ID);

        service.update(PATIENT_ID, ADMISSION, new PersonalData("JUAN carlos", "pérez gómez", LocalDate.of(1996, 3, 15),
                Sex.MALE, "0987654321", "juan.nuevo@correo.ec", address(), "Ingeniero civil", "Superior", true, null,
                null));

        verify(identityRules).checkEmail("juan.nuevo@correo.ec", PORTAL_USER_ID);
        verify(accounts).rename(PORTAL_USER_ID, "Juan Carlos Pérez Gómez");
        verify(accounts).changeEmail(PORTAL_USER_ID, "juan.nuevo@correo.ec");
    }

    @Test
    void unknownPatientIsNotFound() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, ADMISSION, personalData("0991112233", "x@correo.ec")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(PatientRegistrationService.PATIENT_NOT_FOUND);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void searchIsDelegatedToTheRepository() {
        PageRequest pageable = PageRequest.of(1, 5);
        when(patientRepository.search("perez", pageable)).thenReturn(new PageImpl<>(List.of(patient), pageable, 6));

        assertThat(service.search("perez", pageable).getTotalElements()).isEqualTo(6);
        verifyNoInteractions(auditLogService);
    }

    private static PersonalData personalData(String phone, String email) {
        return new PersonalData("Juan Carlos", "Pérez Gómez", LocalDate.of(1996, 3, 15), Sex.MALE, phone, email,
                address(), "Ingeniero civil", "Superior", true, null, null);
    }

    private static PostalAddress address() {
        return new PostalAddress("17", "1701", "170156", "Av. Ilaló", "N12-34", "Río Pita", "Frente al parque");
    }

}
