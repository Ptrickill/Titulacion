package ec.rehabelite.reports.web;

import ec.rehabelite.reports.application.ReportService;
import ec.rehabelite.reports.application.ReportService.ReportFile;
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
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operations reports (HU-14, HU-15): read only, for the operations director. */
@RestController
@RequestMapping(path = "/api/reports", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Indicadores de tratamientos y sesiones (solo dirección operativa, solo lectura)")
@ApiResponse(responseCode = "403", description = "El rol no es dirección operativa; queda auditado como ACCESS_DENIED",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
class ReportController {

    private static final String STATUS_DESCRIPTION = "Estado del tratamiento; sin valor, todos";
    private static final String FROM_DESCRIPTION = "Primer día del rango (yyyy-MM-dd, Quito), incluido: los "
            + "tratamientos que seguían en curso ese día o después y las sesiones de ese día en adelante";
    private static final String TO_DESCRIPTION = "Último día del rango (yyyy-MM-dd, Quito), incluido: los "
            + "tratamientos iniciados hasta ese día y las sesiones hasta ese día";
    private static final String INVALID_PARAMETERS = "Estado del tratamiento o fecha no válidos, o fecha inicial "
            + "posterior a la final";
    private static final String PDF_CACHE = CacheControl.noStore().getHeaderValue();

    private final ReportService reportService;
    private final ReportWebMapper mapper;

    @GetMapping("/summary")
    @Operation(summary = "Indicadores de tratamientos y sesiones",
            description = "Tratamientos activos, finalizados y abandonados, calculados desde el estado actual de cada "
                    + "tratamiento (HU-08) y filtrados por estado, por responsable vigente y por su periodo: con un "
                    + "rango, los que estuvieron en curso algún día del rango. Sesiones registradas (total y por "
                    + "estado), sesiones por fisioterapeuta y su promedio, filtradas por quien registró la sesión, por "
                    + "el estado de su tratamiento y por la fecha de la sesión: con un fisioterapeuta, solo cuentan "
                    + "las sesiones que él registró, aunque el tratamiento tenga hoy otro responsable. Cada consulta "
                    + "queda auditada como READ Report.")
    @ApiResponse(responseCode = "200", description = "Indicadores")
    @ApiResponse(responseCode = "400", description = INVALID_PARAMETERS, content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ReportSummaryResponse summary(
            @Parameter(description = STATUS_DESCRIPTION) @RequestParam(required = false) TreatmentStatusParam status,
            @Parameter(description = "Fisioterapeuta: responsable vigente de los tratamientos y autor de las sesiones")
            @RequestParam(required = false) Long physiotherapistId,
            @Parameter(description = FROM_DESCRIPTION) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO_DESCRIPTION) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return mapper.toResponse(reportService.summary(filter(status, physiotherapistId, from, to)));
    }

    @GetMapping("/treatments")
    @Operation(summary = "Tratamientos del reporte",
            description = "Tratamientos filtrados por estado, por responsable vigente y por su periodo, de la fecha de "
                    + "inicio más reciente a la más antigua, con el nombre del paciente, el diagnóstico CIE-10, el "
                    + "responsable, el estado actual y la fecha de inicio. Sin cédula ni otros datos personales "
                    + "(minimización de datos, LOPDP). Cada tratamiento listado queda auditado como READ.")
    @ApiResponse(responseCode = "200", description = "Página de tratamientos")
    @ApiResponse(responseCode = "400", description = INVALID_PARAMETERS + "; página o tamaño no válidos",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    PageResponse<ReportTreatmentResponse> treatments(
            @Parameter(description = STATUS_DESCRIPTION) @RequestParam(required = false) TreatmentStatusParam status,
            @Parameter(description = "Responsable vigente del tratamiento")
            @RequestParam(required = false) Long physiotherapistId,
            @Parameter(description = FROM_DESCRIPTION) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO_DESCRIPTION) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamaño de página, de 1 a 50")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return PageResponse.of(
                reportService.treatments(filter(status, physiotherapistId, from, to), PageRequest.of(page, size)),
                mapper::toResponse);
    }

    @GetMapping(path = "/summary.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Informe operativo en PDF",
            description = "El reporte con los mismos parámetros y las mismas consultas que la vista: los filtros "
                    + "aplicados, los indicadores, las sesiones por fisioterapeuta y todos los tratamientos del filtro "
                    + "(no solo una página), con la fecha y hora de generación (Quito) y el nombre del usuario. Sin "
                    + "cédula ni otros datos personales. Queda auditado como EXPORT Report y como READ de cada "
                    + "tratamiento del PDF.")
    @ApiResponse(responseCode = "200", description = "PDF del informe (descarga)",
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "400", description = INVALID_PARAMETERS, content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<byte[]> summaryPdf(
            @Parameter(description = STATUS_DESCRIPTION) @RequestParam(required = false) TreatmentStatusParam status,
            @Parameter(description = "Fisioterapeuta: responsable vigente de los tratamientos y autor de las sesiones")
            @RequestParam(required = false) Long physiotherapistId,
            @Parameter(description = FROM_DESCRIPTION) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO_DESCRIPTION) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportFile file = reportService.export(filter(status, physiotherapistId, from, to));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, PDF_CACHE)
                .body(file.content());
    }

    @GetMapping("/physiotherapists")
    @Operation(summary = "Fisioterapeutas para el filtro",
            description = "Todos los fisioterapeutas, activos o no, ordenados por nombre: una cuenta desactivada "
                    + "puede seguir como responsable de tratamientos y autora de sesiones. No se audita.")
    @ApiResponse(responseCode = "200", description = "Fisioterapeutas")
    List<PhysiotherapistOptionResponse> physiotherapists() {
        return mapper.toOptions(reportService.physiotherapists());
    }

    /** Rejects a range whose start is after its end (400) before anything is read. */
    private static ReportFilter filter(TreatmentStatusParam status, Long physiotherapistId, LocalDate from,
            LocalDate to) {
        return new ReportFilter(status == null ? null : status.name(), physiotherapistId, from, to);
    }

}
