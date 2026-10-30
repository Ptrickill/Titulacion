package ec.rehabelite.clinicalrecords.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.clinicalrecords.application.ClinicalRecordViewService.ClinicalRecordView;
import ec.rehabelite.clinicalrecords.domain.ClinicalHistoryData;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecord;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecordRepository;
import ec.rehabelite.clinicalrecords.domain.Icd10Diagnosis;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignment;
import ec.rehabelite.clinicalrecords.domain.ResponsibleAssignmentRepository;
import ec.rehabelite.clinicalrecords.domain.Severity;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.domain.TreatmentData;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatus;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.patients.PatientProfile;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class ClinicalRecordViewServiceTests {

    private static final Long PATIENT_ID = 15L;
    private static final Instant NOW = Instant.parse("2026-10-30T15:00:00Z");

    private final ClinicalRecordRepository recordRepository = mock(ClinicalRecordRepository.class);
    private final ResponsibleAssignmentRepository assignmentRepository = mock(ResponsibleAssignmentRepository.class);
    private final PatientDirectory patientDirectory = mock(PatientDirectory.class);
    private final StaffDirectory staffDirectory = mock(StaffDirectory.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final ClinicalRecordViewService service = new ClinicalRecordViewService(recordRepository,
            assignmentRepository, patientDirectory, staffDirectory, auditLogService);

    private final AtomicLong ids = new AtomicLong(100);
    private ClinicalRecord record;

    @BeforeEach
    void setUp() {
        when(patientDirectory.findProfile(PATIENT_ID)).thenReturn(Optional.of(profile()));
        record = ClinicalRecord.create(PATIENT_ID, history(), 7L, NOW);
        ReflectionTestUtils.setField(record, "id", 40L);
        when(recordRepository.findWithTreatmentsByPatientId(PATIENT_ID)).thenReturn(Optional.of(record));
        when(staffDirectory.fullNames(anyCollection())).thenReturn(Map.of(7L, "Valeria Andrade", 8L, "Martín Cevallos"));
    }

    @Test
    void splitsTheActiveTreatmentFromThePreviousOnesWithTheirResponsiblesAndAuditsTheRead() {
        Treatment oldest = open(LocalDate.of(2025, 1, 10), 7L, TreatmentStatus.COMPLETED);
        Treatment abandoned = open(LocalDate.of(2025, 6, 2), 8L, TreatmentStatus.ABANDONED);
        Treatment active = open(LocalDate.of(2026, 10, 1), 7L, null);
        currentAssignmentsOf(oldest, abandoned, active);

        ClinicalRecordView view = service.view(PATIENT_ID);

        assertThat(view.patient().id()).isEqualTo(PATIENT_ID);
        assertThat(view.record()).isSameAs(record);
        assertThat(view.activeTreatment()).isSameAs(active);
        assertThat(view.previousTreatments()).containsExactly(abandoned, oldest);
        assertThat(view.responsibleOf(abandoned)).hasValueSatisfying(
                assignment -> assertThat(view.nameOf(assignment.getPhysiotherapistId())).isEqualTo("Martín Cevallos"));
        assertThat(view.responsibleOf(active)).hasValueSatisfying(
                assignment -> assertThat(view.nameOf(assignment.getPhysiotherapistId())).isEqualTo("Valeria Andrade"));
        // One query for the responsibles and one for their names, whatever the number of treatments
        verify(assignmentRepository, times(1)).findCurrentByTreatmentIds(anyCollection());
        verify(staffDirectory, times(1)).fullNames(anyCollection());
        verify(auditLogService).record(AuditAction.READ, ClinicalRecordService.CLINICAL_RECORD_ENTITY, 40L);
    }

    @Test
    void withoutAnActiveTreatmentOnlyThePreviousOnesAreListed() {
        Treatment completed = open(LocalDate.of(2025, 1, 10), 7L, TreatmentStatus.COMPLETED);
        currentAssignmentsOf(completed);

        ClinicalRecordView view = service.view(PATIENT_ID);

        assertThat(view.activeTreatment()).isNull();
        assertThat(view.previousTreatments()).containsExactly(completed);
    }

    @Test
    void anUnknownPatientIsNotFoundAndNotAudited() {
        when(patientDirectory.findProfile(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ClinicalRecordService.PATIENT_NOT_FOUND);
        verifyNoInteractions(recordRepository, auditLogService);
    }

    @Test
    void aPatientWithoutARecordIsNotFoundAndNotAudited() {
        when(recordRepository.findWithTreatmentsByPatientId(PATIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view(PATIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ClinicalRecordService.PATIENT_WITHOUT_CLINICAL_RECORD);
        verifyNoInteractions(assignmentRepository, auditLogService);
    }

    /** Opens a treatment on the record and, if a status is given, closes it so the next one can be opened. */
    private Treatment open(LocalDate startDate, Long physiotherapistId, TreatmentStatus closedAs) {
        Treatment treatment = record.openTreatment(diagnosis(), treatment(), physiotherapistId, startDate, NOW);
        ReflectionTestUtils.setField(treatment, "id", ids.incrementAndGet());
        if (closedAs != null) {
            ReflectionTestUtils.setField(treatment, "status", closedAs);
        }
        return treatment;
    }

    private void currentAssignmentsOf(Treatment... treatments) {
        List<ResponsibleAssignment> current = Arrays.stream(treatments)
                .map(treatment -> treatment.currentResponsible().orElseThrow())
                .toList();
        when(assignmentRepository.findCurrentByTreatmentIds(any())).thenReturn(current);
    }

    private static PatientProfile profile() {
        return new PatientProfile(PATIENT_ID, "1710034065", "1710034065", "Juan Carlos", "Pérez Gómez",
                LocalDate.of(1981, 3, 15), "MALE", "0991234567", "paciente@rehabelite.test", "Av. Amazonas",
                "Ingeniero", "Superior", "IESS", LocalDate.of(2026, 7, 8));
    }

    private static ClinicalHistoryData history() {
        return new ClinicalHistoryData("No fuma", "Camina", "Ninguna", "Ninguna", "Ninguno", "Ninguno", "Ninguno");
    }

    private static TreatmentData treatment() {
        return new TreatmentData("Dolor lumbar", "Tres semanas", "Dr. Andrade", "Radiografía", Severity.MODERATE,
                "Punzante", "Lumbar", 7, "Intermitente", "3 por semana", "Reducir el dolor", 12);
    }

    private static Icd10Diagnosis diagnosis() {
        Icd10Diagnosis diagnosis = BeanUtils.instantiateClass(Icd10Diagnosis.class);
        ReflectionTestUtils.setField(diagnosis, "code", "M54.5");
        ReflectionTestUtils.setField(diagnosis, "description", "Lumbago no especificado");
        return diagnosis;
    }

}
