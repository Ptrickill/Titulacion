package ec.rehabelite.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** A message for the user, in Spanish (MEJ-01: the answer of POST /api/auth/forgot-password). */
@Schema(description = "Mensaje para el usuario")
record MessageResponse(
        @Schema(example = "Si el correo corresponde a una cuenta activa, recibirá en unos minutos un enlace para "
                + "restablecer la contraseña.") String message) {
}
