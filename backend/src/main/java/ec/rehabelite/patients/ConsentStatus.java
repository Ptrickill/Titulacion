package ec.rehabelite.patients;

/** State of a consent type for a patient (MEJ-06). */
public enum ConsentStatus {
    /** Given for the version of the text in force. */
    CURRENT,
    /** Given for an earlier version: still valid ("Versión anterior"), the patient may accept the new one. */
    PREVIOUS_VERSION,
    /** Never given ("Consentimiento pendiente"). */
    PENDING,
    /** The last one was revoked. */
    REVOKED
}
