package ec.rehabelite.clinicalrecords;

import java.util.Optional;

/**
 * Public API for the export of the clinical record (MEJ-06), used only by the sessions module, which adds the sessions
 * and builds the PDF and the JSON the physiotherapist hands to the data subject. It neither audits nor checks roles:
 * the caller does both.
 */
public interface ClinicalRecordExportPort {

    /** The whole clinical record of the patient, or empty when the patient has none. */
    Optional<ClinicalRecordDocument> documentOf(Long patientId);

}
