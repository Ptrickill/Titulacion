package ec.rehabelite.patients;

/**
 * Consent of a patient (MEJ-06), each with its own text in {@code resources/legal}. Without a current
 * {@link #DATA_PROCESSING} the patient gets no new clinical record, treatment, session, appointment nor portal access.
 */
public enum ConsentType {
    /** Processing of personal and health data; required to register a new patient. */
    DATA_PROCESSING,
    /**
     * Non-essential email communications (e.g. future reminders). The account emails (activation, password reset,
     * email confirmation and its notice) are essential and do not depend on it.
     */
    EMAIL_COMMUNICATIONS
}
