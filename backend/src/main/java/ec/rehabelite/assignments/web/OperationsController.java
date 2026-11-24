package ec.rehabelite.assignments.web;

import ec.rehabelite.assignments.web.OperationsResponses.AvailablePhysiotherapistResponse;
import ec.rehabelite.assignments.web.OperationsResponses.BoardPhysiotherapistResponse;
import ec.rehabelite.assignments.web.OperationsResponses.OperationsTreatmentResponse;
import ec.rehabelite.assignments.web.OperationsResponses.ScheduleBoardResponse;
import ec.rehabelite.assignments.application.AssignmentService;
import ec.rehabelite.shared.config.AppProperties;
import ec.rehabelite.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/operations", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Horarios y asignaciones",
        description = "Horarios de los fisioterapeutas, disponibilidad y reasignación de tratamientos (MEJ-04, solo "
                + "dirección operativa). Sin datos clínicos.")
class OperationsController {

    private static final String HOUR = "Hora en formato HH:mm, dentro de la jornada de la clínica";

    private final AssignmentService assignmentService;
    private final AppProperties properties;

    @GetMapping("/schedules")
    @Operation(summary = "Tablero semanal de horarios",
            description = "Todos los fisioterapeutas, activos o no, ordenados por apellidos y nombres, con sus franjas "
                    + "de lunes a sábado y su carga (tratamientos activos de los que son responsables vigentes), y la "
                    + "jornada de la clínica. No se audita: no lleva datos clínicos.")
    @ApiResponse(responseCode = "200", description = "Tablero de horarios")
    ScheduleBoardResponse schedules() {
        AppProperties.Clinic clinic = properties.clinic();
        return new ScheduleBoardResponse(clinic.openingTime(), clinic.closingTime(),
                assignmentService.board().stream().map(BoardPhysiotherapistResponse::of).toList());
    }

    @GetMapping("/availability")
    @Operation(summary = "Fisioterapeutas disponibles",
            description = "Fisioterapeutas con la cuenta activa y una franja que cubre todo el rango del día (las "
                    + "franjas contiguas ya están unidas), de menor a mayor carga y luego por apellidos. Cada uno trae "
                    + "sus franjas de ese día. No se audita.")
    @ApiResponse(responseCode = "200", description = "Fisioterapeutas disponibles")
    @ApiResponse(responseCode = "400", description = "Domingo, rango invertido o fuera de la jornada, o parámetro "
            + "faltante o mal escrito", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    List<AvailablePhysiotherapistResponse> availability(
            @Parameter(description = "Día, de MONDAY a SATURDAY") @RequestParam DayOfWeek day,
            @Parameter(description = HOUR, example = "08:00") @RequestParam @DateTimeFormat(pattern = "HH:mm")
            LocalTime from,
            @Parameter(description = HOUR, example = "10:00") @RequestParam @DateTimeFormat(pattern = "HH:mm")
            LocalTime to) {
        return assignmentService.availability(day, from, to).stream()
                .map(load -> AvailablePhysiotherapistResponse.of(load, day))
                .toList();
    }

    @GetMapping("/treatments")
    @Operation(summary = "Tratamientos activos para asignar",
            description = "Tratamientos ACTIVE, del inicio más reciente al más antiguo, con el nombre del paciente, el "
                    + "responsable vigente, si su cuenta está desactivada (responsibleInactive), la fecha de inicio y "
                    + "los días del horario del responsable. Nunca el diagnóstico, el plan, las notas ni las sesiones. "
                    + "Con inactiveResponsible=true, solo los que tienen un responsable con la cuenta desactivada. "
                    + "Cada tratamiento listado queda auditado como READ.")
    @ApiResponse(responseCode = "200", description = "Página de tratamientos activos")
    @ApiResponse(responseCode = "400", description = "Página o tamaño no válidos", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    PageResponse<OperationsTreatmentResponse> treatments(
            @Parameter(description = "Solo los tratamientos con un responsable de cuenta desactivada")
            @RequestParam(defaultValue = "false") boolean inactiveResponsible,
            @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamaño de página, de 1 a 50")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return PageResponse.of(assignmentService.treatments(inactiveResponsible, PageRequest.of(page, size)),
                OperationsTreatmentResponse::of);
    }

    @PutMapping(path = "/treatments/{id}/responsible", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reasignar fisioterapeuta responsable",
            description = "Cierra la asignación vigente y abre otra para el fisioterapeuta, con la misma lógica que "
                    + "PUT /api/treatments/{id}/responsible (HU-08). Solo tratamientos activos y fisioterapeutas "
                    + "activos; la disponibilidad no se exige. Elegir al responsable vigente no cambia ni audita nada; "
                    + "un cambio real queda auditado como UPDATE Treatment, con su revisión de Envers.")
    @ApiResponse(responseCode = "200", description = "El tratamiento con su responsable")
    @ApiResponse(responseCode = "400", description = "Falta el fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El tratamiento no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "El tratamiento no está activo o el elegido no es un "
            + "fisioterapeuta activo", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    OperationsTreatmentResponse reassign(@PathVariable Long id, @Valid @RequestBody ReassignRequest request) {
        return OperationsTreatmentResponse.of(assignmentService.reassign(id, request.physiotherapistId()));
    }

}
