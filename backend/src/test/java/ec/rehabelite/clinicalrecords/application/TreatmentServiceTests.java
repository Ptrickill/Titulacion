package ec.rehabelite.clinicalrecords.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.auth.StaffMember;
import ec.rehabelite.clinicalrecords.application.TreatmentService.TreatmentHistory;
import ec.rehabelite.clinicalrecords.domain.ClinicalHistoryData;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecord;
import ec.rehabelite.clinicalrecords.domain.Icd10Diagnosis;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignment;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignmentRepository;
import ec.rehabelite.clinicalrecords.domain.Severity;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.domain.TreatmentData;
import ec.rehabelite.clinicalrecords.domain.TreatmentRepository;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatus;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatusChange;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatusChangeRepository;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class TreatmentServiceTests {

    private static final Long TREATMENT_ID = 30L;
    private static final Long PHYSIO_ID = 7L;
    private static final Long OTHER_PHYSIO_ID = 8L;
    private static final Instant OPENED_AT = Instant.parse("2026-10-01T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-30T15:00:00Z");

    private final TreatmentRepository treatmentRepository = mock(TreatmentRepository.class);
    private final ResponsibleAssignmentRepository assignmentRepository = mock(ResponsibleAssignmentRepository.class);
    private final TreatmentStatusChangeRepository statusChangeRepository =
            mock(TreatmentStatusChangeRepository.class);
    private final StaffDirectory staffDirectory = mock(StaffDirectory.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final TreatmentService service = new TreatmentService(treatmentRepository, assignmentRepository,
            statusChangeRepository, staffDirectory, currentUserProvider, auditLogService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private Treatment treatment;

    @BeforeEach
    void setUp() {
        ClinicalRecord record = ClinicalRecord.create(15L, new ClinicalHistoryData("No fuma", "Camina", "Ninguna",
                "Ninguna", "Ninguno", "Ninguno", "Ninguno"), PHYSIO_ID, OPENED_AT);
        treatment = record.openTreatment(diagnosis(), new TreatmentData("Dolor lumbar", "Tres semanas",
                "Dr. Andrade", "Radiografía", Severity.MODERATE, "Punzante", "Lumbar", 7, "Intermitente",
                "3 por semana", "Reducir el dolor", 12), PHYSIO_ID, LocalDate.of(2026, 10, 1), OPENED_AT);
        ReflectionTestUtils.setField(treatment, "id", TREATMENT_ID);
        when(treatmentRepository.findForUpdate(TREATMENT_ID)).thenReturn(Optional.of(treatment));
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(OTHER_PHYSIO_ID));
        when(staffDirectory.findActivePhysiotherapist(OTHER_PHYSIO_ID))
                .thenReturn(Optional.of(new StaffMember(OTHER_PHYSIO_ID, "Lcdo. Mateo Ríos")));
        when(staffDirectory.fullNames(anyCollection()))
                .thenReturn(Map.of(PHYSIO_ID, "Lcda. Valeria Andrade", OTHER_PHYSIO_ID, "Lcdo. Mateo Ríos"));
    }

    @Test
    void changesTheStatusKeepsTheChangeAndAuditsIt() {
        when(statusChangeRepository.save(any(TreatmentStatusChange.class))).thenAnswer(invocation -> {
            TreatmentStatusChange change = invocation.getArgument(0);
            when(statusChangeRepository.findByTreatmentIdOrderByChangedAtDescIdDesc(TREATMENT_ID))
                    .thenReturn(List.of(change));
            return change;
        });

        TreatmentHistory history = service.changeStatus(TREATMENT_ID, TreatmentStatus.COMPLETED, "Alta");

        assertThat(treatment.getStatus()).isEqualTo(TreatmentStatus.COMPLETED);
        assertThat(history.statusChanges()).singleElement().satisfies(change -> {
            assertThat(change.getPreviousStatus()).isEqualTo(TreatmentStatus.ACTIVE);
            assertThat(change.getNewStatus()).isEqualTo(TreatmentStatus.COMPLETED);
            assertThat(change.getUserId()).isEqualTo(OTHER_PHYSIO_ID);
            assertThat(change.getChangedAt()).isEqualTo(NOW);
        });
        assertThat(history.nameOf(OTHER_PHYSIO_ID)).isEqualTo("Lcdo. Mateo Ríos");
        verify(auditLogService).record(AuditAction.STATUS_CHANGE, ClinicalRecordService.TREATMENT_ENTITY,
                TREATMENT_ID);
    }

    @Test
    void aTransitionThatIsNotAllowedSavesAndAuditsNothing() {
        ReflectionTestUtils.setField(treatment, "status", TreatmentStatus.COMPLETED);

        assertThatThrownBy(() -> service.changeStatus(TREATMENT_ID, TreatmentStatus.ACTIVE, "Volvió"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(Treatment.STATUS_TRANSITION_NOT_ALLOWED);
        verify(statusChangeRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void changingTheResponsibleFlushesTheEndedAssignmentBeforeSavingTheNewOne() {
        ResponsibleAssignment previous = treatment.currentResponsible().orElseThrow();

        service.changeResponsible(TREATMENT_ID, OTHER_PHYSIO_ID);

        assertThat(previous.getValidTo()).isEqualTo(NOW);
        InOrder order = inOrder(treatmentRepository, assignmentRepository, auditLogService);
        order.verify(treatmentRepository).flush();
        order.verify(assignmentRepository).save(argThat(
                assignment -> assignment.getPhysiotherapistId().equals(OTHER_PHYSIO_ID) && assignment.isCurrent()));
        order.verify(auditLogService).record(AuditAction.UPDATE, ClinicalRecordService.TREATMENT_ENTITY,
                TREATMENT_ID);
    }

    @Test
    void theCurrentResponsibleChangesAndAuditsNothing() {
        when(staffDirectory.findActivePhysiotherapist(PHYSIO_ID))
                .thenReturn(Optional.of(new StaffMember(PHYSIO_ID, "Lcda. Valeria Andrade")));

        service.changeResponsible(TREATMENT_ID, PHYSIO_ID);

        verify(treatmentRepository, never()).flush();
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void theNewResponsibleMustBeAnActivePhysiotherapist() {
        when(staffDirectory.findActivePhysiotherapist(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeResponsible(TREATMENT_ID, 99L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(TreatmentService.RESPONSIBLE_NOT_ACTIVE_PHYSIOTHERAPIST);
        assertThat(treatment.currentResponsible()).hasValueSatisfying(
                assignment -> assertThat(assignment.getPhysiotherapistId()).isEqualTo(PHYSIO_ID));
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void anUnknownTreatmentIsNotFound() {
        when(treatmentRepository.findForUpdate(404L)).thenReturn(Optional.empty());
        when(treatmentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(404L, TreatmentStatus.COMPLETED, "Alta"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(TreatmentService.TREATMENT_NOT_FOUND);
        assertThatThrownBy(() -> service.changeResponsible(404L, OTHER_PHYSIO_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.history(404L))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(auditLogService);
    }

    private static Icd10Diagnosis diagnosis() {
        Icd10Diagnosis diagnosis = BeanUtils.instantiateClass(Icd10Diagnosis.class);
        ReflectionTestUtils.setField(diagnosis, "code", "M54.5");
        ReflectionTestUtils.setField(diagnosis, "description", "Lumbago no especificado");
        return diagnosis;
    }

}
