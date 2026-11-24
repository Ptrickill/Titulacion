package ec.rehabelite.auth.web;

import ec.rehabelite.auth.AccountStatus;
import ec.rehabelite.auth.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Cuenta de usuario (nunca incluye la contraseña)")
record UserResponse(
        @Schema(example = "7") Long id,
        @Schema(example = "lucia.mora@rehabelite.ec") String email,
        @Schema(description = "Nombres y apellidos del registro de la persona", example = "Lucía Fernanda Mora Paz")
        String fullName,
        Role role,
        @Schema(description = "Cuenta habilitada (no desactivada por el administrador)", example = "true")
        boolean active,
        @Schema(description = "Estado de la cuenta: pendiente de activación, activa o inactiva",
                example = "PENDING_ACTIVATION") AccountStatus status,
        @Schema(description = "Tiene un cambio de correo esperando la confirmación de su titular (MEJ-02)",
                example = "false") boolean emailChangePending,
        @Schema(description = "Último inicio de sesión (UTC); nulo si nunca ingresó") Instant lastLoginAt,
        @Schema(description = "Fecha de creación (UTC)") Instant createdAt) {
}
