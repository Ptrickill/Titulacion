package ec.rehabelite.auth.web;

import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.LoginResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Token de acceso y datos del usuario autenticado")
record LoginResponse(
        @Schema(description = "JWT firmado con HS256; enviarlo en la cabecera Authorization: Bearer <token>")
        String token,

        @Schema(description = "Tipo de token", example = "Bearer")
        String tokenType,

        @Schema(description = "Fecha y hora de expiración del token (UTC)", example = "2026-10-28T15:30:00Z")
        Instant expiresAt,

        UserSummary user) {

    static final String BEARER = "Bearer";

    static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.token(), BEARER, result.expiresAt(),
                new UserSummary(result.userId(), result.email(), result.fullName(), result.role()));
    }

    @Schema(description = "Usuario autenticado")
    record UserSummary(
            @Schema(example = "1") Long id,
            @Schema(example = "fisioterapeuta@rehabelite.ec") String email,
            @Schema(example = "María José Andrade Paz") String fullName,
            Role role) {
    }

}
