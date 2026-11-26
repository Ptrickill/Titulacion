package ec.rehabelite.staff.web;

import ec.rehabelite.staff.application.PeopleRegistryService.StaffStatistics;
import io.swagger.v3.oas.annotations.media.Schema;

/** Indicators of the operations director's "Gestión de Personal" (MEJ-05). */
@Schema(description = "Indicadores de la Gestión de Personal")
record StaffStatsResponse(
        @Schema(description = "Cuentas activas (habilitadas y activadas) del personal", example = "6")
        long activeAccounts,
        @Schema(description = "Cuentas de fisioterapeutas, en cualquier estado", example = "3") long physiotherapists,
        @Schema(description = "Cuentas de recepción, en cualquier estado", example = "2") long receptionists,
        @Schema(description = "Cuentas de dirección operativa, en cualquier estado", example = "1") long directors) {

    static StaffStatsResponse of(StaffStatistics statistics) {
        return new StaffStatsResponse(statistics.activeAccounts(), statistics.physiotherapists(),
                statistics.receptionists(), statistics.directors());
    }

}
