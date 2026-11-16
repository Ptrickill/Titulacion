package ec.rehabelite.reports.web;

import ec.rehabelite.reports.application.ReportService;
import ec.rehabelite.reports.application.ReportService.ReportFilter;
import ec.rehabelite.reports.web.ReportResponses.PhysiotherapistOptionResponse;
import ec.rehabelite.reports.web.ReportResponses.ReportSummaryResponse;
import ec.rehabelite.reports.web.ReportResponses.ReportTreatmentResponse;
import ec.rehabelite.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operations reports (HU-14): read only, for the operations director. */
@RestController
@RequestMapping(path = "/api/reports", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Indicadores de tratamientos y sesiones (solo dirección operativa, solo lectura)")
@ApiResponse(responseCode = "403", description = "El rol no es dirección operativa; queda auditado como ACCESS_DENIED",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
class ReportController {

    private static final String STATUS_DESCRIPTION = "Estado del tratamiento; sin valor, todos";

    private final ReportService reportService;
    private final ReportWebMapper mapper;

    @GetMapping("/summary")
    @Operation(summary = "Indicadores de tratamientos y sesiones",
            description = "Tratamientos activos, finalizados y abandonados, calculados desde el estado de cada "
                    + "tratamiento (HU-08) y filtrados por estado y por responsable vigente. Sesiones registradas "
                    + "(total y por estado), sesiones por fisioterapeuta y su promedio, filtradas por quien registró "
                    + "la sesión y por el estado de su tratamiento: con un fisioterapeuta, solo cuentan las sesiones "
                    + "que él registró, aunque el tratamiento tenga hoy otro responsable. Cada consulta queda "
                    + "auditada como READ Report.")
    @ApiResponse(responseCode = "200", description = "Indicadores")
    @ApiResponse(responseCode = "400", description = "Estado del tratamiento no válido", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ReportSummaryResponse summary(
            @Parameter(description = STATUS_DESCRIPTION) @RequestParam(required = false) TreatmentStatusParam status,
            @Parameter(description = "Fisioterapeuta: responsable vigente de los tratamientos y autor de las sesiones")
            @RequestParam(required = false) Long physiotherapistId) {
        return mapper.toResponse(reportService.summary(filter(status, physiotherapistId)));
    }

    @GetMapping("/treatments")
    @Operation(summary = "Tratamientos del reporte",
            description = "Tratamientos filtrados por estado y por responsable vigente, de la fecha de inicio más "
                    + "reciente a la más antigua, con el nombre del paciente, el diagnóstico CIE-10, el responsable, "
                    + "el estado y la fecha de inicio. Sin cédula ni otros datos personales (minimización de datos, "
                    + "LOPDP). Cada tratamiento listado queda auditado como READ.")
    @ApiResponse(responseCode = "200", description = "Página de tratamientos")
    @ApiResponse(responseCode = "400", description = "Estado del tratamiento, página o tamaño no válidos",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    PageResponse<ReportTreatmentResponse> treatments(
            @Parameter(description = STATUS_DESCRIPTION) @RequestParam(required = false) TreatmentStatusParam status,
            @Parameter(description = "Responsable vigente del tratamiento")
            @RequestParam(required = false) Long physiotherapistId,
            @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamaño de página, de 1 a 50")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return PageResponse.of(
                reportService.treatments(filter(status, physiotherapistId), PageRequest.of(page, size)),
                mapper::toResponse);
    }

    @GetMapping("/physiotherapists")
    @Operation(summary = "Fisioterapeutas para el filtro",
            description = "Todos los fisioterapeutas, activos o no, ordenados por nombre: una cuenta desactivada "
                    + "puede seguir como responsable de tratamientos y autora de sesiones. No se audita.")
    @ApiResponse(responseCode = "200", description = "Fisioterapeutas")
    List<PhysiotherapistOptionResponse> physiotherapists() {
        return mapper.toOptions(reportService.physiotherapists());
    }

    private static ReportFilter filter(TreatmentStatusParam status, Long physiotherapistId) {
        return new ReportFilter(status == null ? null : status.name(), physiotherapistId);
    }

}
