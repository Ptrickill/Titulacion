package ec.rehabelite.auth.web;

import ec.rehabelite.auth.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Cuenta de usuario (nunca incluye la contraseña)")
record UserResponse(
        @Schema(example = "7") Long id,
        @Schema(example = "lucia.mora@rehabelite.ec") String email,
        @Schema(example = "Lucía Fernanda Mora Paz") String fullName,
        Role role,
        @Schema(description = "Estado de la cuenta", example = "true") boolean active,
        @Schema(description = "Último inicio de sesión (UTC); nulo si nunca ingresó") Instant lastLoginAt,
        @Schema(description = "Fecha de creación (UTC)") Instant createdAt) {
}
