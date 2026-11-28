package ec.rehabelite.patients.domain;

/** Right the data subject exercises with a request (MEJ-06). */
public enum DataRequestType {
    ACCESS,
    RECTIFICATION,
    UPDATE,
    OBJECTION,
    /** Accepted only by the operations director: blocks the patient and clears its contact data. */
    ERASURE,
    PORTABILITY
}
