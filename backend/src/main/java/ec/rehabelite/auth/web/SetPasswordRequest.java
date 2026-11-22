package ec.rehabelite.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of POST /api/auth/activate and POST /api/auth/reset-password (MEJ-01). */
@Schema(description = "Enlace recibido por correo y la contraseña nueva")
record SetPasswordRequest(
        @Schema(description = "Token del enlace recibido por correo", example = "q3Jx7Q0m...")
        @NotBlank @Size(max = 100) String token,

        @Schema(description = "Contraseña nueva: de 8 a 72 caracteres, con al menos una letra y un número",
                example = "Clave.Segura2026", format = "password")
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$", message = "debe contener al menos una letra y un número")
        String password,

        @Schema(description = "Confirmación: la misma contraseña", example = "Clave.Segura2026", format = "password")
        @NotBlank String passwordConfirmation) {

    /** Never print the token nor the passwords, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "SetPasswordRequest[token=***, password=***, passwordConfirmation=***]";
    }

}
