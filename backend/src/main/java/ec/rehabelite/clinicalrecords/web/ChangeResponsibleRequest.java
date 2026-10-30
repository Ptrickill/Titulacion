package ec.rehabelite.clinicalrecords.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Nuevo fisioterapeuta responsable del tratamiento")
record ChangeResponsibleRequest(
        @Schema(description = "Id de un usuario activo con rol PHYSIOTHERAPIST", example = "12")
        @NotNull Long physiotherapistId) {
}
