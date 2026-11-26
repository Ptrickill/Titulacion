package ec.rehabelite.staff.web;

import ec.rehabelite.staff.application.PeopleRegistryService.PatientStatistics;
import io.swagger.v3.oas.annotations.media.Schema;

/** Indicators of the reception's "Registro de Pacientes" (MEJ-05): patients and their portal accounts. */
@Schema(description = "Indicadores del Registro de Pacientes")
record PeopleStatsResponse(
        @Schema(description = "Pacientes registrados", example = "15") long patients,
        @Schema(description = "Accesos al portal activos (habilitados y activados)", example = "3") long portalActive,
        @Schema(description = "Accesos al portal pendientes de activación", example = "1") long portalPending,
        @Schema(description = "Pacientes sin acceso al portal", example = "11") long withoutPortal) {

    static PeopleStatsResponse of(PatientStatistics statistics) {
        return new PeopleStatsResponse(statistics.patients(), statistics.portalActive(), statistics.portalPending(),
                statistics.withoutPortal());
    }

}
