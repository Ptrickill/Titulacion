package ec.rehabelite.sessions.application;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditLogService;
import ec.rehabelite.audit.EntityVersion;
import ec.rehabelite.audit.EntityVersionHistory;
import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.auth.StaffDirectory;
import ec.rehabelite.clinicalrecords.TreatmentQueryPort;
import ec.rehabelite.clinicalrecords.TreatmentSummary;
import ec.rehabelite.sessions.domain.PainPoint;
import ec.rehabelite.sessions.domain.SessionStatus;
import ec.rehabelite.sessions.domain.TherapySession;
import ec.rehabelite.sessions.domain.TherapySessionData;
import ec.rehabelite.sessions.domain.TherapySessionRepository;
import ec.rehabelite.shared.domain.BusinessRuleException;
import ec.rehabelite.shared.domain.ForbiddenOperationException;
import ec.rehabelite.shared.domain.InvalidRequestException;
import ec.rehabelite.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Therapy sessions of a treatment (HU-10): registration with a server-assigned sequence number and responsible,
 * correction by the physiotherapist who registered the session, and its versions. Treatments are looked up only
 * through {@link TreatmentQueryPort} and staff accounts through {@link StaffDirectory}.
 * <p>
 * Every operation is audited (HU-18); the reads too, so their methods are transactional without readOnly.
 */
@Service
@RequiredArgsConstructor
public class TherapySessionService {

    public static final String SESSION_ENTITY = "TherapySession";
    static final String TREATMENT_ENTITY = "Treatment";
    static final String TREATMENT_NOT_FOUND = "El tratamiento no existe.";
    static final String SESSION_NOT_FOUND = "La sesión no existe.";
    static final String TREATMENT_NOT_ACTIVE = "Solo se pueden registrar sesiones en un tratamiento activo.";
    static final String DATE_BEFORE_TREATMENT_START =
            "La fecha de la sesión no puede ser anterior al inicio del tratamiento.";
    static final String ONLY_THE_AUTHOR_CORRECTS = "Solo el fisioterapeuta que registró la sesión puede corregirla.";
    static final String INVALID_RANGE = "La fecha inicial no puede ser posterior a la fecha final.";

    private static final Logger log = LoggerFactory.getLogger(TherapySessionService.class);
    private static final Sort BY_SEQUENCE = Sort.by("sequenceNumber");

    private final TherapySessionRepository sessionRepository;
    private final TreatmentQueryPort treatmentQuery;
    private final StaffDirectory staffDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final EntityVersionHistory versionHistory;
    private final Clock clock;

    /** A session with the name of its responsible (null if the account no longer exists). */
    public record NamedSession(TherapySession session, String responsibleName) {
    }

    /** A page of sessions with the names of their responsibles. */
    public record SessionPage(Page<TherapySession> sessions, Map<Long, String> userNames) {

        public String nameOf(Long userId) {
            return userNames.get(userId);
        }

    }

    /** The versions of a session (most recent first) with the names of the responsible and the authors. */
    public record SessionVersions(List<EntityVersion<TherapySession>> versions, Map<Long, String> userNames) {

        public String nameOf(Long userId) {
            return userId == null ? null : userNames.get(userId);
        }

    }

    /**
     * The timeline of a treatment (HU-11).
     *
     * @param treatment         starting point: start date, initial pain score, planned sessions and diagnosis
     * @param completedSessions COMPLETED sessions of the whole treatment, whatever the filter (progress of the plan)
     * @param sessions          sessions of the range, in chronological order
     * @param painCurve         initial evaluation followed by the COMPLETED sessions of the range
     */
    public record Timeline(TreatmentSummary treatment, long completedSessions, List<TherapySession> sessions,
            List<PainPoint> painCurve, Map<Long, String> userNames) {

        public String nameOf(Long userId) {
            return userNames.get(userId);
        }

    }

    /**
     * Registers a session on an active treatment. The treatment row stays locked until the transaction ends, so the
     * next sequence number (highest + 1) is never taken twice. The responsible is always the authenticated user.
     *
     * @throws ResourceNotFoundException if the treatment does not exist
     * @throws BusinessRuleException if the treatment is not active, the date is before its start or the pain score
     *         is outside 0–10
     */
    @Transactional
    public NamedSession register(Long treatmentId, TherapySessionData data) {
        TreatmentSummary treatment = treatmentQuery.lockForSessionRegistration(treatmentId)
                .orElseThrow(() -> new ResourceNotFoundException(TREATMENT_NOT_FOUND));
        if (!treatment.active()) {
            throw new BusinessRuleException(TREATMENT_NOT_ACTIVE);
        }
        requireNotBeforeStart(treatment, data);
        Long userId = currentUserId();
        int sequenceNumber = sessionRepository.maxSequenceNumber(treatmentId) + 1;
        TherapySession session = sessionRepository.save(
                TherapySession.register(treatmentId, sequenceNumber, userId, data, clock.instant()));

        auditLogService.record(AuditAction.CREATE, SESSION_ENTITY, session.getId());
        log.info("Therapy session registered sessionId={} treatmentId={} sequenceNumber={} responsibleId={}",
                session.getId(), treatmentId, sequenceNumber, userId);
        return named(session);
    }

    /**
     * The sessions of the treatment ordered by sequence number; the read is audited as READ of the treatment.
     *
     * @throws ResourceNotFoundException if the treatment does not exist (not audited)
     */
    @Transactional
    public SessionPage list(Long treatmentId, int page, int size) {
        if (treatmentQuery.find(treatmentId).isEmpty()) {
            throw new ResourceNotFoundException(TREATMENT_NOT_FOUND);
        }
        Page<TherapySession> sessions =
                sessionRepository.findByTreatmentId(treatmentId, PageRequest.of(page, size, BY_SEQUENCE));
        Set<Long> responsibleIds = new HashSet<>();
        sessions.forEach(session -> responsibleIds.add(session.getResponsibleId()));
        Map<Long, String> names = responsibleIds.isEmpty() ? Map.of() : staffDirectory.fullNames(responsibleIds);

        auditLogService.record(AuditAction.READ, TREATMENT_ENTITY, treatmentId);
        return new SessionPage(sessions, names);
    }

    /**
     * The therapy timeline of a treatment (HU-11): its starting point, the sessions in chronological order between
     * two optional days (both included) with the names of their responsibles, and the pain curve. The read is
     * audited as READ of the treatment. A fixed number of statements, whatever the number of sessions: the
     * treatment, the sessions, the completed count, the names in one call and the audit entry.
     *
     * @throws InvalidRequestException if {@code from} is after {@code to} (nothing is read nor audited)
     * @throws ResourceNotFoundException if the treatment does not exist (not audited)
     */
    @Transactional
    public Timeline timeline(Long treatmentId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException(INVALID_RANGE);
        }
        TreatmentSummary treatment = treatmentQuery.find(treatmentId)
                .orElseThrow(() -> new ResourceNotFoundException(TREATMENT_NOT_FOUND));
        List<TherapySession> sessions = sessionRepository.findTimeline(treatmentId, from, to);
        long completedSessions = sessionRepository.countByTreatmentIdAndStatus(treatmentId, SessionStatus.COMPLETED);
        Set<Long> responsibleIds = new HashSet<>();
        sessions.forEach(session -> responsibleIds.add(session.getResponsibleId()));
        Map<Long, String> names = responsibleIds.isEmpty() ? Map.of() : staffDirectory.fullNames(responsibleIds);

        auditLogService.record(AuditAction.READ, TREATMENT_ENTITY, treatmentId);
        return new Timeline(treatment, completedSessions, sessions,
                PainPoint.curveOf(treatment.startDate(), treatment.initialPainScore(), sessions), names);
    }

    /**
     * One session; the read is audited as READ.
     *
     * @throws ResourceNotFoundException if the session does not exist (not audited)
     */
    @Transactional
    public NamedSession find(Long sessionId) {
        TherapySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(SESSION_NOT_FOUND));
        auditLogService.record(AuditAction.READ, SESSION_ENTITY, sessionId);
        return named(session);
    }

    /**
     * Corrects the editable fields of a session; the sequence number and the responsible stay, and Envers keeps the
     * previous version. Only the physiotherapist who registered the session may correct it, whatever the status of
     * the treatment. The same data changes and audits nothing; a real correction is audited as UPDATE.
     *
     * @throws ResourceNotFoundException if the session does not exist
     * @throws ForbiddenOperationException if the user did not register the session (403, audited as ACCESS_DENIED)
     * @throws BusinessRuleException if the date is before the start of the treatment or the pain score is outside
     *         0–10
     */
    @Transactional
    public NamedSession correct(Long sessionId, TherapySessionData data) {
        TherapySession session = sessionRepository.findForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(SESSION_NOT_FOUND));
        Long userId = currentUserId();
        if (!session.isRegisteredBy(userId)) {
            log.info("Therapy session correction denied sessionId={} userId={}", sessionId, userId);
            throw new ForbiddenOperationException(ONLY_THE_AUTHOR_CORRECTS);
        }
        TreatmentSummary treatment = treatmentQuery.find(session.getTreatmentId())
                .orElseThrow(() -> new IllegalStateException("A therapy session without its treatment"));
        requireNotBeforeStart(treatment, data);

        if (session.correct(data, userId, clock.instant())) {
            auditLogService.record(AuditAction.UPDATE, SESSION_ENTITY, sessionId);
            log.info("Therapy session corrected sessionId={} correctedBy={}", sessionId, userId);
        }
        return named(session);
    }

    /**
     * Every version of the session kept by Envers, most recent first, with who made each one and when; the read is
     * audited as READ of the session.
     *
     * @throws ResourceNotFoundException if the session does not exist (not audited)
     */
    @Transactional
    public SessionVersions versions(Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException(SESSION_NOT_FOUND);
        }
        List<EntityVersion<TherapySession>> versions =
                versionHistory.versionsOf(TherapySession.class, sessionId).reversed();
        Set<Long> userIds = new HashSet<>();
        versions.forEach(version -> {
            userIds.add(version.entity().getResponsibleId());
            userIds.add(version.changedBy());
        });
        userIds.remove(null);

        auditLogService.record(AuditAction.READ, SESSION_ENTITY, sessionId);
        return new SessionVersions(versions, userIds.isEmpty() ? Map.of() : staffDirectory.fullNames(userIds));
    }

    private static void requireNotBeforeStart(TreatmentSummary treatment, TherapySessionData data) {
        if (data.sessionDate().isBefore(treatment.startDate())) {
            throw new BusinessRuleException(DATE_BEFORE_TREATMENT_START);
        }
    }

    private NamedSession named(TherapySession session) {
        Long responsibleId = session.getResponsibleId();
        return new NamedSession(session, staffDirectory.fullNames(Set.of(responsibleId)).get(responsibleId));
    }

    private Long currentUserId() {
        return currentUserProvider.currentUserId()
                .orElseThrow(() -> new IllegalStateException("A therapy session change needs an authenticated user"));
    }

}
