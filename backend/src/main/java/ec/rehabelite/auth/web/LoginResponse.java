package ec.rehabelite.auth.web;

import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.LoginResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.Instant;

@Schema(description = "Token de acceso y datos del usuario autenticado")
record LoginResponse(
        @Schema(description = "JWT firmado con HS256; enviarlo en la cabecera Authorization: Bearer <token>")
        String token,

        @Schema(description = "Tipo de token", example = "Bearer")
        String tokenType,

        @Schema(description = "Fecha y hora de expiración del token (UTC)", example = "2026-10-28T15:30:00Z")
        Instant expiresAt,

        UserSummary user,

        @Schema(description = "MEJ-06: la sesión se cierra tras este tiempo sin actividad (segundos, "
                + "app.session.idle-timeout); el límite máximo sigue siendo expiresAt", example = "900")
        long idleTimeoutSeconds,

        @Schema(description = "MEJ-06: segundos antes del cierre por inactividad en que se avisa (app.session.idle-warning)",
                example = "120")
        long idleWarningSeconds) {

    static final String BEARER = "Bearer";

    static LoginResponse from(LoginResult result, Duration idleTimeout, Duration idleWarning) {
        return new LoginResponse(result.token(), BEARER, result.expiresAt(),
                new UserSummary(result.userId(), result.email(), result.fullName(), result.role()),
                idleTimeout.toSeconds(), idleWarning.toSeconds());
    }

    @Schema(description = "Usuario autenticado")
    record UserSummary(
            @Schema(example = "1") Long id,
            @Schema(example = "fisioterapeuta@rehabelite.ec") String email,
            @Schema(example = "María José Andrade Paz") String fullName,
            Role role) {
    }

}
