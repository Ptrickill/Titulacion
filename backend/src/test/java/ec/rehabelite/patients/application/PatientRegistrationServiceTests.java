package ec.rehabelite.patients.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.patients.domain.PatientRepository;
import ec.rehabelite.patients.domain.PersonalData;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ConflictException;
import ec.rehabelite.shared.domain.PostalAddress;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import ec.rehabelite.shared.domain.Sex;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PatientRegistrationServiceTests {

    private static final Long ADMIN_ID = 1L;
    private static final Long NEW_PATIENT_ID = 25L;
    private static final String NATIONAL_ID = "1710034065";
    /** 02:00 UTC on 30 October is still 29 October in Quito (UTC-5). */
    private static final Instant NOW = Instant.parse("2026-10-30T02:00:00Z");

    private final PatientRepository patientRepository = mock(PatientRepository.class);
    private final PatientIdentityRules identityRules = mock(PatientIdentityRules.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final ConsentService consentService = mock(ConsentService.class);

    private final PatientRegistrationService service = new PatientRegistrationService(patientRepository,
            identityRules, currentUserProvider, auditLogService, consentService, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(ADMIN_ID));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> {
            Patient patient = invocation.getArgument(0);
            ReflectionTestUtils.setField(patient, "id", NEW_PATIENT_ID);
            return patient;
        });
    }

    @Test
    void registersThePatientAndAuditsTheCreation() {
        Patient patient = service.register(command(NATIONAL_ID, LocalDate.of(2026, 10, 20)));

        assertThat(patient.getId()).isEqualTo(NEW_PATIENT_ID);
        assertThat(patient.getMedicalRecordNumber()).isEqualTo(NATIONAL_ID);
        assertThat(patient.getAdmissionDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(patient.getCreatedBy()).isEqualTo(ADMIN_ID);
        assertThat(patient.getCreatedAt()).isEqualTo(NOW);
        assertThat(patient.getReligion()).isNull();
        verify(identityRules).checkNationalId(NATIONAL_ID);
        verify(identityRules).checkEmail("juan.perez@correo.ec", null);
        verify(auditLogService).record(AuditAction.CREATE, PatientRegistrationService.AUDITED_ENTITY, NEW_PATIENT_ID);
    }

    @Test
    void withoutAdmissionDateUsesTodayInTheClinic() {
        Patient patient = service.register(command(NATIONAL_ID, null));

        assertThat(patient.getAdmissionDate()).isEqualTo(LocalDate.of(2026, 10, 29));
    }

    @Test
    void aRegisteredNationalIdIsAConflictWithoutSavingOrAuditing() {
        doThrow(new ConflictException(PatientIdentityRules.DUPLICATE_NATIONAL_ID,
                Map.of(PatientIdentityRules.EXISTING_PATIENT_ID, 12L))).when(identityRules)
                .checkNationalId(NATIONAL_ID);

        assertThatThrownBy(() -> service.register(command(NATIONAL_ID, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage(PatientIdentityRules.DUPLICATE_NATIONAL_ID);
        verify(patientRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void birthDateAfterAdmissionIsRejectedWithoutSavingOrAuditing() {
        assertThatThrownBy(() -> service.register(command(NATIONAL_ID, LocalDate.of(1990, 1, 1))))
                .isInstanceOf(BusinessRuleException.class);
        verify(patientRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void unknownPatientIsNotFound() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(PatientRegistrationService.PATIENT_NOT_FOUND);
    }

    private static RegisterPatientCommand command(String nationalId, LocalDate admissionDate) {
        return new RegisterPatientCommand(nationalId, admissionDate, personalData(), "0.1-borrador", null);
    }

    private static PersonalData personalData() {
        return new PersonalData("Juan Carlos", "Pérez Gómez", LocalDate.of(1996, 3, 15), Sex.MALE, "0987654321",
                "juan.perez@correo.ec",
                new PostalAddress("17", "1701", "170156", "Av. Ilaló", "N12-34", "Río Pita", "Frente al parque"),
                "Ingeniero civil", "Superior", true, "Mestizo", "");
    }

}
