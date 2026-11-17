package ec.rehabelite.clinicalrecords;

import java.time.LocalDate;

/**
 * Which treatments an operations report (HU-14, HU-15) counts or lists. Every criterion is optional: a null one
 * does not filter.
 * <p>
 * The period (HU-15) keeps the treatments that were in progress some day between {@code from} and {@code to}, both
 * included, in Quito: those that started on or before {@code to} and, by {@code from}, were still active, had started
 * or closed. A treatment closes on the date of its last status change; the status is always the current one.
 *
 * @param status        value of treatments.status: ACTIVE, COMPLETED or ABANDONED; null for every status
 * @param responsibleId user id of the current responsible physiotherapist; null for any responsible
 * @param from          first day of the period; null for no lower bound
 * @param to            last day of the period; null for no upper bound
 */
public record TreatmentFilter(String status, Long responsibleId, LocalDate from, LocalDate to) {

    /** Every treatment. */
    public static final TreatmentFilter ALL = new TreatmentFilter(null, null);

    /** Without a period (HU-14). */
    public TreatmentFilter(String status, Long responsibleId) {
        this(status, responsibleId, null, null);
    }

}
