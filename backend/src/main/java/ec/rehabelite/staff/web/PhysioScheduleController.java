package ec.rehabelite.staff.web;

import ec.rehabelite.shared.config.AppProperties;
import ec.rehabelite.staff.application.StaffScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Mi horario" (MEJ-04): the physiotherapist reads its own weekly schedule, identified by the token. */
@RestController
@RequestMapping(path = "/api/physio/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Horarios", description = "Horario semanal de los fisioterapeutas (MEJ-04)")
class PhysioScheduleController {

    private final StaffScheduleService scheduleService;
    private final AppProperties properties;

    @GetMapping
    @Operation(summary = "Consultar mi horario",
            description = "Horario semanal del fisioterapeuta autenticado, de solo lectura: lo editan el "
                    + "administrador y la dirección operativa. La cuenta sale del token; no recibe ids. No se audita.")
    @ApiResponse(responseCode = "200", description = "Horario del fisioterapeuta, con la jornada de la clínica")
    @ApiResponse(responseCode = "403", description = "El rol no es fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "La cuenta no tiene un registro del personal", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ScheduleResponse ownSchedule() {
        return ScheduleResponse.of(scheduleService.ownSchedule(), properties.clinic());
    }

}
