package ec.rehabelite.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Link token of the "Confirme su nuevo correo" email (MEJ-02). */
@Schema(description = "Enlace del correo de confirmación del nuevo correo")
record ConfirmEmailRequest(
        @Schema(description = "Token del enlace del correo") @NotBlank @Size(max = 100) String token) {

    /** The token is a credential: never print it. */
    @Override
    public String toString() {
        return "ConfirmEmailRequest[...]";
    }

}
