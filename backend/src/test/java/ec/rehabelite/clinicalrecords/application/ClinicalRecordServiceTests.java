package ec.rehabelite.clinicalrecords.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.clinicalrecords.domain.ClinicalHistoryData;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecord;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecordRepository;
import ec.rehabelite.clinicalrecords.domain.Icd10Diagnosis;
import ec.rehabelite.clinicalrecords.domain.Icd10DiagnosisRepository;
import ec.rehabelite.clinicalrecords.domain.Severity;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.domain.TreatmentData;
import ec.rehabelite.clinicalrecords.domain.TreatmentRepository;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatus;
import ec.rehabelite.patients.PatientCard;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ConflictException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class ClinicalRecordServiceTests {

    private static final Long PHYSIO_ID = 7L;
    private static final Long PATIENT_ID = 15L;
    /** 02:00 UTC on 31 October is still 30 October in Quito. */
    private static final Instant NOW = Instant.parse("2026-10-31T02:00:00Z");

    private final ClinicalRecordRepository recordRepository = mock(ClinicalRecordRepository.class);
    private final TreatmentRepository treatmentRepository = mock(TreatmentRepository.class);
    private final Icd10DiagnosisRepository diagnosisRepository = mock(Icd10DiagnosisRepository.class);
    private final PatientDirectory patientDirectory = mock(PatientDirectory.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final ClinicalRecordService service = new ClinicalRecordService(recordRepository, treatmentRepository, diagnosisRepository,
            patientDirectory, currentUserProvider, auditLogService, Clock.fixed(NOW, ZoneOffset.UTC));

    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(PHYSIO_ID));
        when(patientDirectory.find(PATIENT_ID)).thenReturn(Optional.of(new PatientCard(PATIENT_ID, "1710034065",
                "1710034065", "Juan Carlos", "Pérez Gómez", LocalDate.of(1996, 3, 15))));
        when(recordRepository.findByPatientId(any())).thenReturn(Optional.empty());
        when(diagnosisRepository.findById("M54.5")).thenReturn(Optional.of(diagnosis()));
        // Simulates the identity columns: the record and its cascaded treatments get ids on save
        when(recordRepository.save(any(ClinicalRecord.class))).thenAnswer(invocation -> assignIds(invocation.getArgument(0)));
        when(treatmentRepository.save(any(Treatment.class))).thenAnswer(invocation -> {
            Treatment treatment = invocation.getArgument(0);
            ReflectionTestUtils.setField(treatment, "id", ids.incrementAndGet());
            return treatment;
        });
    }

    @Test
    void createsTheRecordWithAnActiveTreatmentAndAuditsBoth() {
        ClinicalRecord record = service.create(PATIENT_ID, history(), " m54.5 ", treatment());

        Treatment treatment = record.getTreatments().getFirst();
        assertThat(record.getPatientId()).isEqualTo(PATIENT_ID);
        assertThat(record.getCreatedBy()).isEqualTo(PHYSIO_ID);
        assertThat(treatment.getStatus()).isEqualTo(TreatmentStatus.ACTIVE);
        assertThat(treatment.getDiagnosis().getCode()).isEqualTo("M54.5");
        assertThat(treatment.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(treatment.currentResponsible()).hasValueSatisfying(
                assignment -> assertThat(assignment.getPhysiotherapistId()).isEqualTo(PHYSIO_ID));
        verify(auditLogService).record(AuditAction.CREATE, ClinicalRecordService.CLINICAL_RECORD_ENTITY, record.getId());
        verify(auditLogService).record(AuditAction.CREATE, ClinicalRecordService.TREATMENT_ENTITY, treatment.getId());
    }

    @Test
    void aPatientWithARecordIsAConflictWithTheExistingRecordId() {
        ClinicalRecord existing = ClinicalRecord.create(PATIENT_ID, history(), PHYSIO_ID, NOW);
        ReflectionTestUtils.setField(existing, "id", 40L);
        when(recordRepository.findByPatientId(PATIENT_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(PATIENT_ID, history(), "M54.5", treatment()))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex).hasMessage(ClinicalRecordService.CLINICAL_RECORD_EXISTS);
                    assertThat(ex.getProperties())
                            .containsExactlyEntriesOf(Map.of(ClinicalRecordService.EXISTING_CLINICAL_RECORD_ID, 40L));
                });
        verify(recordRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void anUnknownPatientIsNotFound() {
        when(patientDirectory.find(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L, history(), "M54.5", treatment()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ClinicalRecordService.PATIENT_NOT_FOUND);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void aDiagnosisOutsideTheCatalogIsRejected() {
        when(diagnosisRepository.findById("Z99.9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(PATIENT_ID, history(), "Z99.9", treatment()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(ClinicalRecordService.DIAGNOSIS_NOT_FOUND);
        verify(recordRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void opensANewTreatmentOnAnExistingRecordOnceThePreviousOneIsClosed() {
        ClinicalRecord record = ClinicalRecord.create(PATIENT_ID, history(), PHYSIO_ID, NOW);
        assignIds(record);
        Treatment previous = record.openTreatment(diagnosis(), treatment(), PHYSIO_ID, LocalDate.of(2026, 1, 5), NOW);
        ReflectionTestUtils.setField(previous, "status", TreatmentStatus.COMPLETED);
        when(recordRepository.findById(record.getId())).thenReturn(Optional.of(record));

        Treatment opened = service.openTreatment(record.getId(), "M54.5", treatment());

        assertThat(opened.isActive()).isTrue();
        assertThat(record.getTreatments()).hasSize(2);
        verify(auditLogService).record(AuditAction.CREATE, ClinicalRecordService.TREATMENT_ENTITY, opened.getId());
    }

    @Test
    void openingATreatmentOnAnUnknownRecordIsNotFound() {
        when(recordRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.openTreatment(404L, "M54.5", treatment()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ClinicalRecordService.CLINICAL_RECORD_NOT_FOUND);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updatesTheHistoryAndAuditsTheChange() {
        ClinicalRecord record = assignIds(ClinicalRecord.create(PATIENT_ID, history(), 3L, NOW.minusSeconds(86_400)));
        when(recordRepository.findByPatientId(PATIENT_ID)).thenReturn(Optional.of(record));
        ClinicalHistoryData edited = new ClinicalHistoryData("Fuma", "Natación", "Penicilina", "Paracetamol",
                "Diabetes tipo 2", "Ninguno", "Padre hipertenso");

        ClinicalRecord updated = service.updateHistory(PATIENT_ID, edited);

        assertThat(updated.historyData()).isEqualTo(edited);
        assertThat(updated.getUpdatedBy()).isEqualTo(PHYSIO_ID);
        assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
        verify(auditLogService).record(AuditAction.UPDATE, ClinicalRecordService.CLINICAL_RECORD_ENTITY, record.getId());
    }

    @Test
    void theSameHistoryIsNotAudited() {
        ClinicalRecord record = assignIds(ClinicalRecord.create(PATIENT_ID, history(), 3L, NOW));
        when(recordRepository.findByPatientId(PATIENT_ID)).thenReturn(Optional.of(record));

        service.updateHistory(PATIENT_ID, history());

        assertThat(record.getUpdatedAt()).isNull();
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updatingWithoutARecordIsNotFoundAndTellsAnUnknownPatientApart() {
        assertThatThrownBy(() -> service.updateHistory(PATIENT_ID, history()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ClinicalRecordService.PATIENT_WITHOUT_CLINICAL_RECORD);

        when(patientDirectory.find(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateHistory(99L, history()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(ClinicalRecordService.PATIENT_NOT_FOUND);
        verifyNoInteractions(auditLogService);
    }

    private ClinicalRecord assignIds(ClinicalRecord record) {
        if (record.getId() == null) {
            ReflectionTestUtils.setField(record, "id", ids.incrementAndGet());
        }
        record.getTreatments().stream().filter(t -> t.getId() == null)
                .forEach(t -> ReflectionTestUtils.setField(t, "id", ids.incrementAndGet()));
        return record;
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
