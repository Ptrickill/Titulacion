package ec.rehabelite.staff.web;

import ec.rehabelite.auth.AccountStatistics;
import io.swagger.v3.oas.annotations.media.Schema;

/** Indicators of "Registro de Usuarios" (MEJ-02). */
@Schema(description = "Indicadores del Registro de Usuarios")
record PeopleStatsResponse(
        @Schema(description = "Cuentas activas (habilitadas y activadas), de todos los roles", example = "7")
        long activeAccounts,
        @Schema(description = "Cuentas de fisioterapeutas, en cualquier estado", example = "3") long physiotherapists,
        @Schema(description = "Cuentas de pacientes con acceso al portal, en cualquier estado", example = "4")
        long portalPatients,
        @Schema(description = "Cuentas de administradores, en cualquier estado", example = "1") long administrators) {

    static PeopleStatsResponse of(AccountStatistics statistics) {
        return new PeopleStatsResponse(statistics.activeAccounts(), statistics.physiotherapists(),
                statistics.portalPatients(), statistics.administrators());
    }

}
