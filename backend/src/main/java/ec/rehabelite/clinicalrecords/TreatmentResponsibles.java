package ec.rehabelite.clinicalrecords;

/**
 * Public API of the clinicalrecords module for the operations director (MEJ-04): reassigns the responsible
 * physiotherapist of a treatment with the same domain logic as {@code PUT /api/treatments/{id}/responsible} (HU-08).
 */
public interface TreatmentResponsibles {

    /**
     * Ends the current assignment and starts one for the physiotherapist, as HU-08 does: locks the treatment, audits
     * UPDATE Treatment and leaves an Envers revision by the authenticated user. Choosing the current responsible
     * changes and audits nothing.
     *
     * @return the treatment with its responsible
     * @throws ec.rehabelite.shared.domain.ResourceNotFoundException if the treatment does not exist
     * @throws ec.rehabelite.shared.domain.BusinessRuleException if the user is not an active physiotherapist or the
     *         treatment is not active
     */
    ActiveTreatment reassign(Long treatmentId, Long physiotherapistId);

}
