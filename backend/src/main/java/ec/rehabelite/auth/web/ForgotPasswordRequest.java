package ec.rehabelite.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/auth/forgot-password (MEJ-01). */
@Schema(description = "Correo de la cuenta cuya contraseña se quiere restablecer")
record ForgotPasswordRequest(
        @Schema(description = "Correo electrónico de la cuenta", example = "lucia.mora@rehabelite.ec")
        @NotBlank @Email @Size(max = 254) String email) {

    /** Never print the email, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "ForgotPasswordRequest[email=***]";
    }

}
