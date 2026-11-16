package ec.rehabelite.reports.domain;

import ec.rehabelite.sessions.SessionCount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The indicators of the operations report (HU-14). The treatments and the sessions are filtered separately, so this
 * type only adds up what it receives:
 * <ul>
 * <li>treatments per status, from the status of each treatment (HU-08);</li>
 * <li>sessions per status and per physiotherapist who registered them;</li>
 * <li>the average of sessions among the physiotherapists with at least one session, with one decimal.</li>
 * </ul>
 *
 * @param sessionsByPhysiotherapist from the most sessions to the fewest, then by name
 * @param averageSessionsPerPhysiotherapist total sessions over the physiotherapists of
 *        {@code sessionsByPhysiotherapist}, rounded half up to one decimal; 0.0 if there are none
 */
public record OperationsSummary(TreatmentCounts treatments, SessionCounts sessions,
        List<PhysiotherapistSessions> sessionsByPhysiotherapist, BigDecimal averageSessionsPerPhysiotherapist) {

    static final String ACTIVE = "ACTIVE";
    static final String COMPLETED = "COMPLETED";
    static final String ABANDONED = "ABANDONED";
    static final String RESCHEDULED = "RESCHEDULED";
    static final String NO_SHOW = "NO_SHOW";

    private static final Comparator<PhysiotherapistSessions> MOST_SESSIONS_FIRST = Comparator
            .comparingLong(PhysiotherapistSessions::sessions).reversed()
            .thenComparing(PhysiotherapistSessions::name, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PhysiotherapistSessions::physiotherapistId);

    public OperationsSummary {
        sessionsByPhysiotherapist = List.copyOf(sessionsByPhysiotherapist);
    }

    /** Treatments per status. */
    public record TreatmentCounts(long active, long completed, long abandoned) {
    }

    /** Sessions registered, in total and per status. */
    public record SessionCounts(long total, long completed, long rescheduled, long noShow) {
    }

    /**
     * Sessions registered by one physiotherapist, whatever their status.
     *
     * @param name full name of the account; null if it no longer exists
     */
    public record PhysiotherapistSessions(Long physiotherapistId, String name, long sessions) {
    }

    /**
     * @param treatmentsByStatus treatments per value of treatments.status; a missing status counts as 0
     * @param sessionCounts      sessions per physiotherapist who registered them and per status
     * @param names              full name of each physiotherapist of {@code sessionCounts}
     */
    public static OperationsSummary of(Map<String, Long> treatmentsByStatus, List<SessionCount> sessionCounts,
            Map<Long, String> names) {
        TreatmentCounts treatments = new TreatmentCounts(treatmentsByStatus.getOrDefault(ACTIVE, 0L),
                treatmentsByStatus.getOrDefault(COMPLETED, 0L), treatmentsByStatus.getOrDefault(ABANDONED, 0L));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<Long, Long> byPhysiotherapist = new LinkedHashMap<>();
        for (SessionCount count : sessionCounts) {
            byStatus.merge(count.status(), count.sessions(), Long::sum);
            byPhysiotherapist.merge(count.responsibleId(), count.sessions(), Long::sum);
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        SessionCounts sessions = new SessionCounts(total, byStatus.getOrDefault(COMPLETED, 0L),
                byStatus.getOrDefault(RESCHEDULED, 0L), byStatus.getOrDefault(NO_SHOW, 0L));

        List<PhysiotherapistSessions> perPhysiotherapist = byPhysiotherapist.entrySet().stream()
                .map(entry -> new PhysiotherapistSessions(entry.getKey(), names.get(entry.getKey()),
                        entry.getValue()))
                .sorted(MOST_SESSIONS_FIRST)
                .toList();
        return new OperationsSummary(treatments, sessions, perPhysiotherapist,
                average(total, perPhysiotherapist.size()));
    }

    private static BigDecimal average(long sessions, int physiotherapists) {
        if (physiotherapists == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(sessions).divide(BigDecimal.valueOf(physiotherapists), 1, RoundingMode.HALF_UP);
    }

}
