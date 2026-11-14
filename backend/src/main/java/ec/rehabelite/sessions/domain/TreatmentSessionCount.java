package ec.rehabelite.sessions.domain;

/** Number of sessions of a treatment; row of a grouped count. */
public interface TreatmentSessionCount {

    Long getTreatmentId();

    long getSessions();

}
