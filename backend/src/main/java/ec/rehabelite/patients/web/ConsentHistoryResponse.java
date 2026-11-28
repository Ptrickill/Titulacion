package ec.rehabelite.patients.web;

import ec.rehabelite.patients.ConsentChannel;
import ec.rehabelite.patients.ConsentType;
import ec.rehabelite.patients.domain.PatientConsent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** One consent of the history (MEJ-06), never deleted. */
@Schema(description = "Consentimiento del historial del paciente")
record ConsentHistoryResponse(
        @Schema(example = "7") Long id,
        @Schema(description = "Tipo", example = "DATA_PROCESSING") ConsentType type,
        @Schema(description = "Versión del texto aceptado", example = "0.1-borrador") String textVersion,
        @Schema(description = "Cómo se dio", example = "PHYSICAL_SIGNATURE") ConsentChannel channel,
        @Schema(description = "Fecha y hora en que se dio") Instant grantedAt,
        @Schema(description = "Reemplazado por la aceptación de una versión nueva; null si no") Instant replacedAt,
        @Schema(description = "Fecha y hora de la revocación; null si no se revocó") Instant revokedAt,
        @Schema(description = "Cómo se revocó; null si no se revocó", example = "PORTAL")
        ConsentChannel revocationChannel) {

    static ConsentHistoryResponse of(PatientConsent consent) {
        return new ConsentHistoryResponse(consent.getId(), consent.getType(), consent.getTextVersion(),
                consent.getChannel(), consent.getGrantedAt(), consent.getReplacedAt(), consent.getRevokedAt(),
                consent.getRevocationChannel());
    }

}
