package ec.rehabelite.sessions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.audit.EntityVersion;
import ec.rehabelite.audit.EntityVersionHistory;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import ec.rehabelite.clinicalrecords.TreatmentSummary;
import ec.rehabelite.sessions.application.TherapySessionService.NamedSession;
import ec.rehabelite.sessions.application.TherapySessionService.SessionVersions;
import ec.rehabelite.sessions.application.TherapySessionService.Timeline;
import ec.rehabelite.sessions.domain.PainPoint;
import ec.rehabelite.sessions.domain.ProgressStatus;
import ec.rehabelite.sessions.domain.SessionStatus;
import ec.rehabelite.sessions.domain.TherapySession;
import ec.rehabelite.sessions.domain.TherapySessionData;
import ec.rehabelite.sessions.domain.TherapySessionRepository;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ForbiddenOperationException;
import ec.rehabelite.shared.domain.InvalidRequestException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class TherapySessionServiceTests {

    private static final Long TREATMENT_ID = 30L;
    private static final Long SESSION_ID = 90L;
    private static final Long PHYSIO_ID = 7L;
    private static final Long OTHER_PHYSIO_ID = 8L;
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final Instant NOW = Instant.parse("2026-11-05T15:00:00Z");

    private final TherapySessionRepository sessionRepository = mock(TherapySessionRepository.class);
    private final TreatmentQueryPort treatmentQuery = mock(TreatmentQueryPort.class);
    private final StaffDirectory staffDirectory = mock(StaffDirectory.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final EntityVersionHistory versionHistory = mock(EntityVersionHistory.class);

    private final TherapySessionService service = new TherapySessionService(sessionRepository, treatmentQuery,
            staffDirectory, currentUserProvider, auditLogService, versionHistory, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        TreatmentSummary active = treatment(true);
        when(treatmentQuery.lockForSessionRegistration(TREATMENT_ID)).thenReturn(Optional.of(active));
        when(treatmentQuery.find(TREATMENT_ID)).thenReturn(Optional.of(active));
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(PHYSIO_ID));
        when(staffDirectory.fullNames(anyCollection()))
                .thenReturn(Map.of(PHYSIO_ID, "Lcda. Valeria Andrade", OTHER_PHYSIO_ID, "Lcdo. Mateo Ríos"));
        when(sessionRepository.save(any(TherapySession.class))).thenAnswer(invocation -> {
            TherapySession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", SESSION_ID);
            return session;
        });
    }

    // ----- registration -----

    @Test
    void registersWithTheNextSequenceNumberAndTheAuthenticatedResponsibleAndAuditsIt() {
        when(sessionRepository.maxSequenceNumber(TREATMENT_ID)).thenReturn(4);

        NamedSession registered = service.register(TREATMENT_ID, data(LocalDate.of(2026, 11, 4), 5));

        assertThat(registered.session().getSequenceNumber()).isEqualTo(5);
        assertThat(registered.session().getResponsibleId()).isEqualTo(PHYSIO_ID);
        assertThat(registered.session().getCreatedAt()).isEqualTo(NOW);
        assertThat(registered.responsibleName()).isEqualTo("Lcda. Valeria Andrade");
        // The treatment is locked before the highest sequence number is read
        InOrder order = inOrder(treatmentQuery, sessionRepository, auditLogService);
        order.verify(treatmentQuery).lockForSessionRegistration(TREATMENT_ID);
        order.verify(sessionRepository).maxSequenceNumber(TREATMENT_ID);
        order.verify(sessionRepository).save(any(TherapySession.class));
        order.verify(auditLogService).record(AuditAction.CREATE, "TherapySession", SESSION_ID);
    }

    @Test
    void theFirstSessionOfATreatmentIsNumberOne() {
        when(sessionRepository.maxSequenceNumber(TREATMENT_ID)).thenReturn(0);

        assertThat(service.register(TREATMENT_ID, data(START, 5)).session().getSequenceNumber()).isEqualTo(1);
    }

    @Test
    void anUnknownTreatmentIsNotFound() {
        when(treatmentQuery.lockForSessionRegistration(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(99L, data(START, 5)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("El tratamiento no existe.");
        verifyNoInteractions(auditLogService);
    }

    @Test
    void aTreatmentThatIsNotActiveRejectsSessions() {
        when(treatmentQuery.lockForSessionRegistration(TREATMENT_ID))
                .thenReturn(Optional.of(treatment(false)));

        assertThatThrownBy(() -> service.register(TREATMENT_ID, data(START, 5)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Solo se pueden registrar sesiones en un tratamiento activo.");
        verify(sessionRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void aSessionCannotBeDatedBeforeTheTreatmentStarts() {
        assertThatThrownBy(() -> service.register(TREATMENT_ID, data(START.minusDays(1), 5)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("La fecha de la sesión no puede ser anterior al inicio del tratamiento.");
        verify(sessionRepository, never()).save(any());
    }

    // ----- list -----

    @Test
    void anUnknownTreatmentHasNoSessionList() {
        when(treatmentQuery.find(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(99L, 0, 20)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(auditLogService);
    }

    // ----- correction -----

    @Test
    void theAuthorCorrectsTheSessionAndTheCorrectionIsAudited() {
        TherapySession session = existingSession(PHYSIO_ID);

        NamedSession corrected = service.correct(SESSION_ID, data(LocalDate.of(2026, 11, 4), 2));

        assertThat(corrected.session().getPainScore()).isEqualTo((short) 2);
        assertThat(session.getSequenceNumber()).isEqualTo(3);
        assertThat(session.getResponsibleId()).isEqualTo(PHYSIO_ID);
        assertThat(session.getUpdatedBy()).isEqualTo(PHYSIO_ID);
        verify(auditLogService).record(AuditAction.UPDATE, "TherapySession", SESSION_ID);
    }

    @Test
    void anotherPhysiotherapistCannotCorrectTheSession() {
        TherapySession session = existingSession(PHYSIO_ID);
        when(currentUserProvider.currentUserId()).thenReturn(Optional.of(OTHER_PHYSIO_ID));

        assertThatThrownBy(() -> service.correct(SESSION_ID, data(LocalDate.of(2026, 11, 4), 2)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Solo el fisioterapeuta que registró la sesión puede corregirla.");
        assertThat(session.getPainScore()).isEqualTo((short) 6);
        assertThat(session.getUpdatedAt()).isNull();
        // The ACCESS_DENIED entry is written by the security handler that answers the 403, not here
        verifyNoInteractions(auditLogService);
    }

    @Test
    void theSameDataIsNeitherChangedNorAudited() {
        TherapySession session = existingSession(PHYSIO_ID);

        service.correct(SESSION_ID, session.data());

        assertThat(session.getUpdatedAt()).isNull();
        verifyNoInteractions(auditLogService);
    }

    @Test
    void anUnknownSessionCannotBeCorrected() {
        when(sessionRepository.findForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.correct(99L, data(START, 2)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("La sesión no existe.");
    }

    // ----- versions -----

    @Test
    void theVersionsAreMostRecentFirstWithTheNamesOfTheirAuthors() {
        when(sessionRepository.existsById(SESSION_ID)).thenReturn(true);
        TherapySession created = TherapySession.register(TREATMENT_ID, 1, PHYSIO_ID, data(START, 6), NOW);
        TherapySession corrected = TherapySession.register(TREATMENT_ID, 1, PHYSIO_ID, data(START, 3), NOW);
        when(versionHistory.versionsOf(TherapySession.class, SESSION_ID)).thenReturn(List.of(
                new EntityVersion<>(created, 10, EntityVersion.Type.CREATE, NOW, PHYSIO_ID),
                new EntityVersion<>(corrected, 12, EntityVersion.Type.UPDATE, NOW.plusSeconds(60), null)));

        SessionVersions versions = service.versions(SESSION_ID);

        assertThat(versions.versions()).extracting(EntityVersion::revision).containsExactly(12L, 10L);
        assertThat(versions.nameOf(PHYSIO_ID)).isEqualTo("Lcda. Valeria Andrade");
        assertThat(versions.nameOf(null)).isNull();
        verify(auditLogService).record(AuditAction.READ, "TherapySession", SESSION_ID);
    }

    // ----- timeline (HU-11) -----

    @Test
    void theTimelineHasTheSessionsTheirResponsiblesAndThePainCurveAndIsAudited() {
        TherapySession first = session(1, PHYSIO_ID, LocalDate.of(2026, 10, 3), 6, SessionStatus.COMPLETED);
        TherapySession rescheduled = session(2, OTHER_PHYSIO_ID, LocalDate.of(2026, 10, 6), 6,
                SessionStatus.RESCHEDULED);
        TherapySession noShow = session(3, OTHER_PHYSIO_ID, LocalDate.of(2026, 10, 8), 6, SessionStatus.NO_SHOW);
        TherapySession last = session(4, OTHER_PHYSIO_ID, LocalDate.of(2026, 10, 10), 3, SessionStatus.COMPLETED);
        when(sessionRepository.findTimeline(TREATMENT_ID, null, null))
                .thenReturn(List.of(first, rescheduled, noShow, last));
        when(sessionRepository.countByTreatmentIdAndStatus(TREATMENT_ID, SessionStatus.COMPLETED)).thenReturn(2L);

        Timeline timeline = service.timeline(TREATMENT_ID, null, null);

        assertThat(timeline.sessions()).containsExactly(first, rescheduled, noShow, last);
        assertThat(timeline.treatment().initialPainScore()).isEqualTo(7);
        assertThat(timeline.completedSessions()).isEqualTo(2);
        assertThat(timeline.nameOf(PHYSIO_ID)).isEqualTo("Lcda. Valeria Andrade");
        assertThat(timeline.nameOf(OTHER_PHYSIO_ID)).isEqualTo("Lcdo. Mateo Ríos");
        // Only COMPLETED sessions are points, after the initial evaluation
        assertThat(timeline.painCurve()).extracting(PainPoint::label, PainPoint::date, PainPoint::painScore)
                .containsExactly(tuple("Evaluación inicial", START, 7),
                        tuple("Sesión 1", LocalDate.of(2026, 10, 3), 6),
                        tuple("Sesión 4", LocalDate.of(2026, 10, 10), 3));
        // The names of every responsible come from a single call
        verify(staffDirectory).fullNames(Set.of(PHYSIO_ID, OTHER_PHYSIO_ID));
        verify(auditLogService).record(AuditAction.READ, "Treatment", TREATMENT_ID);
    }

    @Test
    void theTimelinePassesTheRangeAndStillStartsWithTheInitialEvaluation() {
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 5);
        when(sessionRepository.findTimeline(TREATMENT_ID, from, to)).thenReturn(List.of());

        Timeline timeline = service.timeline(TREATMENT_ID, from, to);

        assertThat(timeline.sessions()).isEmpty();
        assertThat(timeline.painCurve()).singleElement()
                .satisfies(point -> {
                    assertThat(point.label()).isEqualTo("Evaluación inicial");
                    assertThat(point.sessionId()).isNull();
                });
        verifyNoInteractions(staffDirectory);
        verify(auditLogService).record(AuditAction.READ, "Treatment", TREATMENT_ID);
    }

    @Test
    void aStartAfterTheEndIsRejectedWithoutReadingNorAuditing() {
        assertThatThrownBy(() -> service.timeline(TREATMENT_ID, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 8)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("La fecha inicial no puede ser posterior a la fecha final.");
        verifyNoInteractions(treatmentQuery, sessionRepository, auditLogService);
    }

    @Test
    void anUnknownTreatmentHasNoTimeline() {
        when(treatmentQuery.find(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.timeline(99L, null, null))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("El tratamiento no existe.");
        verifyNoInteractions(sessionRepository, auditLogService);
    }

    private static TreatmentSummary treatment(boolean active) {
        return new TreatmentSummary(TREATMENT_ID, 15L, active, START, 7, 12, "M54.5", "Lumbago no especificado");
    }

    private static TherapySession session(int sequenceNumber, Long responsibleId, LocalDate date, int painScore,
            SessionStatus status) {
        TherapySession session = TherapySession.register(TREATMENT_ID, sequenceNumber, responsibleId,
                new TherapySessionData(date, LocalTime.of(9, 30), "Fisioterapia lumbar", "Movilización lumbar",
                        "Buena tolerancia", null, painScore, ProgressStatus.IMPROVING, status), NOW);
        ReflectionTestUtils.setField(session, "id", 100L + sequenceNumber);
        return session;
    }

    private TherapySession existingSession(Long responsibleId) {
        TherapySession session = TherapySession.register(TREATMENT_ID, 3, responsibleId, data(START, 6),
                NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        when(sessionRepository.findForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        return session;
    }

    private static TherapySessionData data(LocalDate date, int painScore) {
        return new TherapySessionData(date, LocalTime.of(9, 30), "Fisioterapia lumbar", "Movilización lumbar",
                "Buena tolerancia", null, painScore, ProgressStatus.IMPROVING, SessionStatus.COMPLETED);
    }

}
