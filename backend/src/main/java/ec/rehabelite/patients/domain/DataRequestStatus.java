package ec.rehabelite.patients.domain;

/** Status of a request of the data subject (MEJ-06): open while RECEIVED or IN_PROGRESS. */
public enum DataRequestStatus {
    RECEIVED,
    IN_PROGRESS,
    RESOLVED,
    REJECTED
}
