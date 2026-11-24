package ec.rehabelite.patients.web;

import ec.rehabelite.auth.AccountStatus;
import ec.rehabelite.auth.AccountSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** The patient's portal account (MEJ-02): never its email nor its password. */
@Schema(description = "Cuenta de acceso al portal del paciente")
record PortalAccountResponse(
        @Schema(description = "Id de la cuenta, para reenviar la invitación", example = "31") Long userId,
        @Schema(description = "Estado de la cuenta", example = "PENDING_ACTIVATION") AccountStatus status,
        @Schema(description = "Tiene un cambio de correo esperando la confirmación de su titular", example = "false")
        boolean emailChangePending,
        @Schema(description = "Último inicio de sesión (UTC); null si nunca ingresó") Instant lastLoginAt) {

    static PortalAccountResponse of(AccountSummary account) {
        return account == null
                ? null
                : new PortalAccountResponse(account.userId(), account.status(), account.emailChangePending(),
                        account.lastLoginAt());
    }

}
