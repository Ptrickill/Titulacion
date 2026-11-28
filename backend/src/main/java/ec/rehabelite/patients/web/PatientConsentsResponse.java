package ec.rehabelite.patients.web;

import ec.rehabelite.patients.PatientConsentView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** The consents of a patient (MEJ-06): the status of each type and the whole history. */
@Schema(description = "Consentimientos del paciente")
record PatientConsentsResponse(
        @Schema(description = "Estado de cada tipo: CURRENT (vigente), PREVIOUS_VERSION (versión anterior, sigue "
                + "vigente), PENDING (pendiente) o REVOKED (revocado); DATA_PROCESSING primero")
        List<PatientConsentView> consents,
        @Schema(description = "Todos los consentimientos, del más reciente al más antiguo")
        List<ConsentHistoryResponse> history) {
}
