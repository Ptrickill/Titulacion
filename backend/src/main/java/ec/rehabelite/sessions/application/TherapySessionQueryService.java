package ec.rehabelite.sessions.application;

import ec.rehabelite.sessions.SessionSummary;
import ec.rehabelite.sessions.TherapySessionQueryPort;
import ec.rehabelite.sessions.domain.SessionStatus;
import ec.rehabelite.sessions.domain.TherapySession;
import ec.rehabelite.sessions.domain.TherapySessionRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements {@link TherapySessionQueryPort} for the other modules. */
@Service
@RequiredArgsConstructor
class TherapySessionQueryService implements TherapySessionQueryPort {

    private final TherapySessionRepository sessionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SessionSummary> chronological(Long treatmentId) {
        return sessionRepository.findTimeline(treatmentId, null, null).stream()
                .map(TherapySessionQueryService::summaryOf)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> completedCounts(Collection<Long> treatmentIds) {
        Map<Long, Long> counts = new HashMap<>();
        treatmentIds.forEach(treatmentId -> counts.put(treatmentId, 0L));
        if (!counts.isEmpty()) {
            sessionRepository.countByTreatmentIdsAndStatus(counts.keySet(), SessionStatus.COMPLETED)
                    .forEach(count -> counts.put(count.getTreatmentId(), count.getSessions()));
        }
        return Map.copyOf(counts);
    }

    private static SessionSummary summaryOf(TherapySession session) {
        return new SessionSummary(session.getId(), session.getTreatmentId(), session.getSequenceNumber(),
                session.getSessionDate(), session.getSessionTime(), session.getStatus().name(),
                session.getStatus() == SessionStatus.COMPLETED, session.getPainScore());
    }

}
