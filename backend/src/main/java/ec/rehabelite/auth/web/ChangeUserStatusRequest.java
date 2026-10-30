package ec.rehabelite.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Nuevo estado de la cuenta")
record ChangeUserStatusRequest(
        @Schema(description = "true para activar la cuenta, false para desactivarla", example = "false")
        @NotNull Boolean active) {
}
