package ec.rehabelite.sessions;

/**
 * How many sessions a physiotherapist registered with one status (HU-14); a row of a grouped count. Only ids and a
 * number: nothing of the content of the sessions.
 *
 * @param responsibleId user id of the physiotherapist who registered the sessions (therapy_sessions.responsible_id)
 * @param status        value of therapy_sessions.status: COMPLETED, RESCHEDULED or NO_SHOW
 */
public record SessionCount(Long responsibleId, String status, long sessions) {
}
