package ec.rehabelite.portal.application;

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
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.clinicalrecords.PatientTreatment;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import ec.rehabelite.clinicalrecords.TreatmentSummary;
import ec.rehabelite.patients.PatientCard;
import ec.rehabelite.patients.PatientDirectory;
import ec.rehabelite.portal.application.PortalService.PortalInstruction;
import ec.rehabelite.portal.application.PortalService.PortalSession;
import ec.rehabelite.portal.application.PortalService.PortalTreatment;
import ec.rehabelite.portal.domain.TreatmentProgress;
import ec.rehabelite.sessions.PatientSessionView;
import ec.rehabelite.sessions.SessionSummary;
import ec.rehabelite.sessions.TherapySessionQueryPort;
import ec.rehabelite.shared.domain.ForbiddenOperationException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PortalServiceTests {

    private static final Long USER_ID = 40L;
    private static final Long PATIENT_ID = 8L;
    private static final Long OTHER_PATIENT_ID = 9L;
    private static final Long VALERIA = 3L;
    private static final Long MATEO = 4L;
    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final PatientDirectory patientDirectory = mock(PatientDirectory.class);
    private final TreatmentQueryPort treatmentQuery = mock(TreatmentQueryPort.class);
    private final TherapySessionQueryPort sessionQuery = mock(TherapySessionQueryPort.class);
    private final StaffDirectory staffDirectory = mock(StaffDirectory.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private final PortalService service = new PortalService(currentUserProvider, patientDirectory, treatmentQuery,
            sessionQuery, staffDirectory, auditLogService);

    @BeforeEach
    void aPatientIsLoggedIn() {
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(USER_ID));
        when(patientDirectory.findByPortalUser(USER_ID)).thenReturn(Optional.of(
                new PatientCard(PATIENT_ID, "1750000018", "1750000018", "Rosa Elena", "Ortiz Vega",
                        LocalDate.of(1985, 4, 12))));
    }

    @Test
    void theTreatmentsKeepTheirOrderAndCarryTheResponsibleAndTheSessionsThatTookPlace() {
        when(treatmentQuery.findByPatient(PATIENT_ID)).thenReturn(List.of(
                listed(30L, "ACTIVE", VALERIA), listed(20L, "COMPLETED", VALERIA), listed(10L, "ABANDONED", null)));
        when(staffDirectory.fullNames(Set.of(VALERIA))).thenReturn(Map.of(VALERIA, "Valeria Andrade Mora"));
        when(sessionQuery.completedCounts(List.of(30L, 20L, 10L))).thenReturn(Map.of(30L, 4L, 20L, 10L, 10L, 0L));

        List<PortalTreatment> treatments = service.treatments();

        assertThat(treatments).extracting(listed -> listed.treatment().id()).containsExactly(30L, 20L, 10L);
        assertThat(treatments).extracting(PortalTreatment::responsibleName)
                .containsExactly("Valeria Andrade Mora", "Valeria Andrade Mora", null);
        assertThat(treatments).extracting(PortalTreatment::completedSessions).containsExactly(4L, 10L, 0L);
    }

    @Test
    void everyTreatmentListedIsAuditedAsReadInOneCall() {
        when(treatmentQuery.findByPatient(PATIENT_ID))
                .thenReturn(List.of(listed(30L, "ACTIVE", VALERIA), listed(20L, "COMPLETED", VALERIA)));
        when(staffDirectory.fullNames(Set.of(VALERIA))).thenReturn(Map.of(VALERIA, "Valeria Andrade Mora"));
        when(sessionQuery.completedCounts(List.of(30L, 20L))).thenReturn(Map.of(30L, 1L, 20L, 2L));

        service.treatments();

        verify(auditLogService).recordAll(AuditAction.READ, "Treatment", List.of(30L, 20L));
    }

    @Test
    void aPatientWithoutTreatmentsGetsAnEmptyListAndNothingIsAudited() {
        when(treatmentQuery.findByPatient(PATIENT_ID)).thenReturn(List.of());

        assertThat(service.treatments()).isEmpty();

        verifyNoInteractions(staffDirectory, sessionQuery, auditLogService);
    }

    @Test
    void treatmentsWithoutAResponsibleDoNotAskForNames() {
        when(treatmentQuery.findByPatient(PATIENT_ID)).thenReturn(List.of(listed(10L, "ABANDONED", null)));
        when(sessionQuery.completedCounts(List.of(10L))).thenReturn(Map.of(10L, 0L));

        assertThat(service.treatments()).singleElement()
                .satisfies(listed -> assertThat(listed.responsibleName()).isNull());

        verifyNoInteractions(staffDirectory);
    }

    @Test
    void anAccountWithoutALinkedPatientGets404AndNothingIsReadNorAudited() {
        when(patientDirectory.findByPortalUser(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(service::treatments)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("La cuenta no tiene un paciente vinculado.");
        assertThatThrownBy(() -> service.progress(30L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("La cuenta no tiene un paciente vinculado.");

        verifyNoInteractions(treatmentQuery, sessionQuery, auditLogService);
    }

    @Test
    void theProgressOfAnOwnTreatmentIsAuditedAsRead() {
        when(treatmentQuery.find(30L)).thenReturn(Optional.of(summary(30L, PATIENT_ID)));
        when(sessionQuery.chronological(30L)).thenReturn(List.of(
                new SessionSummary(1L, 30L, 1, START.plusDays(2), LocalTime.of(9, 30), "COMPLETED", true, 5),
                new SessionSummary(2L, 30L, 2, START.plusDays(4), LocalTime.of(9, 30), "NO_SHOW", false, 9)));

        TreatmentProgress progress = service.progress(30L);

        assertThat(progress.completedSessions()).isEqualTo(1);
        assertThat(progress.plannedSessions()).isEqualTo(12);
        assertThat(progress.painReduction()).isEqualTo(2);
        verify(auditLogService).record(AuditAction.READ, "Treatment", 30L);
    }

    @Test
    void aTreatmentOfAnotherPatientIsForbiddenAndItsSessionsAreNeverRead() {
        when(treatmentQuery.find(31L)).thenReturn(Optional.of(summary(31L, OTHER_PATIENT_ID)));

        assertThatThrownBy(() -> service.progress(31L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("No tiene acceso a ese tratamiento.");

        verifyNoInteractions(sessionQuery);
        // ACCESS_DENIED is recorded by the security handler, not here
        verify(auditLogService, never()).record(any(), any(), any());
    }

    @Test
    void aTreatmentThatDoesNotExistGetsTheSameAnswerAsOneOfAnotherPatient() {
        when(treatmentQuery.find(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.progress(999L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("No tiene acceso a ese tratamiento.");

        verifyNoInteractions(sessionQuery, auditLogService);
    }

    // ----- HU-13: sessions and instructions -----

    @Test
    void theSessionsKeepTheirOrderAndCarryTheNameOfTheirPhysiotherapistFromOneCall() {
        when(treatmentQuery.find(30L)).thenReturn(Optional.of(summary(30L, PATIENT_ID)));
        when(sessionQuery.patientView(30L)).thenReturn(List.of(
                view(3, MATEO, "COMPLETED", "Caminata suave diaria"), view(1, VALERIA, "COMPLETED", null),
                view(2, VALERIA, "NO_SHOW", null)));
        when(staffDirectory.fullNames(Set.of(VALERIA, MATEO)))
                .thenReturn(Map.of(VALERIA, "Valeria Andrade Mora", MATEO, "Mateo Ríos Paz"));

        List<PortalSession> sessions = service.sessions(30L);

        assertThat(sessions).extracting(listed -> listed.session().sequenceNumber()).containsExactly(3, 1, 2);
        assertThat(sessions).extracting(PortalSession::responsibleName)
                .containsExactly("Mateo Ríos Paz", "Valeria Andrade Mora", "Valeria Andrade Mora");
        verify(staffDirectory).fullNames(Set.of(VALERIA, MATEO));
        verify(auditLogService).record(AuditAction.READ, "Treatment", 30L);
    }

    @Test
    void aTreatmentWithoutSessionsHasAnEmptyListThatIsAuditedWithoutAskingForNames() {
        when(treatmentQuery.find(30L)).thenReturn(Optional.of(summary(30L, PATIENT_ID)));
        when(sessionQuery.patientView(30L)).thenReturn(List.of());

        assertThat(service.sessions(30L)).isEmpty();

        verifyNoInteractions(staffDirectory);
        verify(auditLogService).record(AuditAction.READ, "Treatment", 30L);
    }

    @Test
    void theNameIsNullWhenTheAccountOfThePhysiotherapistNoLongerExists() {
        when(treatmentQuery.find(30L)).thenReturn(Optional.of(summary(30L, PATIENT_ID)));
        when(sessionQuery.patientView(30L)).thenReturn(List.of(view(1, VALERIA, "COMPLETED", null)));
        when(staffDirectory.fullNames(Set.of(VALERIA))).thenReturn(Map.of());

        assertThat(service.sessions(30L)).singleElement()
                .satisfies(listed -> assertThat(listed.responsibleName()).isNull());
    }

    @Test
    void theLastInstructionComesWithItsSessionAndThePhysiotherapistWhoWroteIt() {
        when(treatmentQuery.find(30L)).thenReturn(Optional.of(summary(30L, PATIENT_ID)));
        when(sessionQuery.latestPatientInstruction(30L))
                .thenReturn(Optional.of(view(4, MATEO, "NO_SHOW", "Reagendar la sesión esta semana")));
        when(staffDirectory.fullNames(Set.of(MATEO))).thenReturn(Map.of(MATEO, "Mateo Ríos Paz"));

        Optional<PortalInstruction> instruction = service.latestInstruction(30L);

        assertThat(instruction).contains(new PortalInstruction("Reagendar la sesión esta semana",
                START.plusDays(4), 4, "Mateo Ríos Paz"));
        verify(auditLogService).record(AuditAction.READ, "Treatment", 30L);
    }

    @Test
    void aTreatmentWithoutInstructionsHasNoneAndTheReadIsStillAudited() {
        when(treatmentQuery.find(30L)).thenReturn(Optional.of(summary(30L, PATIENT_ID)));
        when(sessionQuery.latestPatientInstruction(30L)).thenReturn(Optional.empty());

        assertThat(service.latestInstruction(30L)).isEmpty();

        verifyNoInteractions(staffDirectory);
        verify(auditLogService).record(AuditAction.READ, "Treatment", 30L);
    }

    @Test
    void theSessionsAndInstructionsOfAnotherPatientOrOfAMissingTreatmentAreForbiddenAndNeverRead() {
        when(treatmentQuery.find(31L)).thenReturn(Optional.of(summary(31L, OTHER_PATIENT_ID)));
        when(treatmentQuery.find(999L)).thenReturn(Optional.empty());

        for (Long treatmentId : List.of(31L, 999L)) {
            assertThatThrownBy(() -> service.sessions(treatmentId))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("No tiene acceso a ese tratamiento.");
            assertThatThrownBy(() -> service.latestInstruction(treatmentId))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("No tiene acceso a ese tratamiento.");
        }

        verifyNoInteractions(sessionQuery, staffDirectory, auditLogService);
    }

    @Test
    void anAccountWithoutALinkedPatientGets404OnTheSessionsAndTheInstructions() {
        when(patientDirectory.findByPortalUser(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sessions(30L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("La cuenta no tiene un paciente vinculado.");
        assertThatThrownBy(() -> service.latestInstruction(30L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("La cuenta no tiene un paciente vinculado.");

        verifyNoInteractions(treatmentQuery, sessionQuery, auditLogService);
    }

    /** A session of the day {@code sequenceNumber} of the treatment. */
    private static PatientSessionView view(int sequenceNumber, Long responsibleId, String status,
            String instructions) {
        return new PatientSessionView(sequenceNumber, START.plusDays(sequenceNumber), LocalTime.of(9, 30),
                "Fisioterapia lumbar mecánica", responsibleId, 5, "Movilización lumbar", status, instructions);
    }

    private static PatientTreatment listed(Long id, String status, Long responsibleId) {
        return new PatientTreatment(id, PATIENT_ID, status, "ACTIVE".equals(status), START, 7, 12, "M54.5",
                "Lumbago no especificado", responsibleId);
    }

    private static TreatmentSummary summary(Long id, Long patientId) {
        return new TreatmentSummary(id, patientId, true, START, 7, 12, "M54.5", "Lumbago no especificado");
    }

}
