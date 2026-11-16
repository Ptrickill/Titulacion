package ec.rehabelite.clinicalrecords;

/**
 * Which treatments an operations report (HU-14) counts or lists. Both criteria are optional: a null one does not
 * filter.
 *
 * @param status        value of treatments.status: ACTIVE, COMPLETED or ABANDONED; null for every status
 * @param responsibleId user id of the current responsible physiotherapist; null for any responsible
 */
public record TreatmentFilter(String status, Long responsibleId) {

    /** Every treatment. */
    public static final TreatmentFilter ALL = new TreatmentFilter(null, null);

}
