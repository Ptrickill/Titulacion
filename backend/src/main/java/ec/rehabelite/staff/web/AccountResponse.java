package ec.rehabelite.staff.web;

import ec.rehabelite.auth.AccountStatus;
import ec.rehabelite.auth.AccountSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** The account of a person in the registry (MEJ-02): never its email nor its password. */
@Schema(description = "Cuenta de la persona")
record AccountResponse(
        @Schema(description = "Id de la cuenta, para reenviar la invitación o cambiar su estado", example = "12")
        Long userId,
        @Schema(description = "Estado de la cuenta", example = "ACTIVE") AccountStatus status,
        @Schema(description = "Tiene un cambio de correo esperando la confirmación de su titular", example = "false")
        boolean emailChangePending,
        @Schema(description = "Último inicio de sesión (UTC); null si nunca ingresó") Instant lastLoginAt) {

    static AccountResponse of(AccountSummary account) {
        return account == null
                ? null
                : new AccountResponse(account.userId(), account.status(), account.emailChangePending(),
                        account.lastLoginAt());
    }

}
