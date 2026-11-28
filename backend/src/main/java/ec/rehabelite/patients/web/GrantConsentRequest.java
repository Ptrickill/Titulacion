package ec.rehabelite.patients.web;

import ec.rehabelite.patients.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A consent signed by the patient, for the version of the text that was shown (MEJ-06). */
@Schema(description = "Consentimiento firmado o aceptado por el paciente")
record GrantConsentRequest(
        @Schema(description = "Tipo de consentimiento", example = "DATA_PROCESSING") @NotNull ConsentType type,
        @Schema(description = "Versión vigente del texto mostrado (GET /api/legal/consents)", example = "0.1-borrador")
        @NotBlank @Size(max = 30) String version) {
}
