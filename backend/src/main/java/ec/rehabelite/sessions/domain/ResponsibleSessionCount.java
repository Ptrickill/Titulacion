package ec.rehabelite.sessions.domain;

/** Sessions a physiotherapist registered with one status; row of a grouped count (HU-14). */
public interface ResponsibleSessionCount {

    Long getResponsibleId();

    String getStatus();

    long getSessions();

}
