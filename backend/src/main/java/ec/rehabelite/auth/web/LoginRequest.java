package ec.rehabelite.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credenciales de inicio de sesión")
record LoginRequest(
        @Schema(description = "Correo electrónico del usuario", example = "fisioterapeuta@rehabelite.ec")
        @NotBlank @Email @Size(max = 254) String email,

        @Schema(description = "Contraseña", example = "Clave.Segura2026", format = "password")
        @NotBlank @Size(max = 72) String password) {

    /** Never print the password, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "LoginRequest[email=***, password=***]";
    }

}
