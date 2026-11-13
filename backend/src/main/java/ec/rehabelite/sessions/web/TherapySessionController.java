package ec.rehabelite.sessions.web;

import ec.rehabelite.sessions.application.TherapySessionService;
import ec.rehabelite.sessions.application.TherapySessionService.NamedSession;
import ec.rehabelite.sessions.application.TherapySessionService.SessionPage;
import ec.rehabelite.sessions.web.TherapySessionResponses.StaffResponse;
import ec.rehabelite.sessions.web.TherapySessionResponses.TherapySessionResponse;
import ec.rehabelite.sessions.web.TherapySessionResponses.TherapySessionVersionResponse;
import ec.rehabelite.sessions.web.TherapySessionResponses.TimelineResponse;
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
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Therapy sessions (HU-10). There is no DELETE: a session is never deleted, it is corrected. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sesiones terapéuticas", description = "Registro, corrección y versiones de sesiones (solo fisioterapeuta)")
class TherapySessionController {

    private final TherapySessionService sessionService;
    private final TherapySessionWebMapper mapper;

    @PostMapping(path = "/api/treatments/{treatmentId}/therapy-sessions", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar una sesión terapéutica",
            description = "Registra la sesión en un tratamiento ACTIVE. El servidor asigna el número correlativo "
                    + "(el mayor del tratamiento + 1, con el tratamiento bloqueado) y toma como responsable al "
                    + "fisioterapeuta autenticado; si el cuerpo trae responsable o correlativo, se ignoran. La fecha no "
                    + "puede ser posterior a hoy ni anterior al inicio del tratamiento. Queda auditada como CREATE.")
    @ApiResponse(responseCode = "201", description = "Sesión registrada")
    @ApiResponse(responseCode = "400", description = "Campo obligatorio vacío, EVA fuera de 0 a 10 o fecha futura",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El tratamiento no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "El tratamiento no está activo o la fecha es anterior a su "
            + "inicio", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<TherapySessionResponse> register(@PathVariable Long treatmentId,
            @Valid @RequestBody TherapySessionRequest request) {
        NamedSession session = sessionService.register(treatmentId, request.toData());
        return ResponseEntity.created(URI.create("/api/therapy-sessions/" + session.session().getId()))
                .body(mapper.toResponse(session));
    }

    @GetMapping(path = "/api/treatments/{treatmentId}/therapy-sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Sesiones del tratamiento",
            description = "Sesiones paginadas en orden de correlativo, con el nombre del responsable. Cada consulta "
                    + "queda auditada como READ del tratamiento.")
    @ApiResponse(responseCode = "200", description = "Página de sesiones")
    @ApiResponse(responseCode = "403", description = "El rol no es fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El tratamiento no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    PageResponse<TherapySessionResponse> list(@PathVariable Long treatmentId,
            @Parameter(description = "Número de página, desde 0", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamaño de página (1 a 100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        SessionPage sessions = sessionService.list(treatmentId, page, size);
        return PageResponse.of(sessions.sessions(), session -> mapper.toResponse(session,
                new StaffResponse(session.getResponsibleId(), sessions.nameOf(session.getResponsibleId()))));
    }

    @GetMapping(path = "/api/treatments/{treatmentId}/timeline", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Línea de tiempo del tratamiento",
            description = "Sesiones del tratamiento en orden cronológico (fecha, hora y correlativo) con su "
                    + "responsable, el punto de partida (fecha de inicio, EVA inicial, sesiones planificadas y "
                    + "diagnóstico) y la curva de EVA: la evaluación inicial seguida de las sesiones COMPLETED. Las "
                    + "fechas filtran por días de Quito, ambas incluidas. Cada consulta queda auditada como READ del "
                    + "tratamiento.")
    @ApiResponse(responseCode = "200", description = "Línea de tiempo")
    @ApiResponse(responseCode = "400", description = "Fecha inicial posterior a la final, o fecha con formato no "
            + "válido", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El tratamiento no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    TimelineResponse timeline(@PathVariable Long treatmentId,
            @Parameter(description = "Desde (día, inclusive)", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Hasta (día, inclusive)", example = "2026-10-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return mapper.toResponse(sessionService.timeline(treatmentId, from, to), from, to);
    }

    @GetMapping(path = "/api/therapy-sessions/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar una sesión", description = "La sesión con su responsable. Queda auditada como READ.")
    @ApiResponse(responseCode = "200", description = "Sesión")
    @ApiResponse(responseCode = "403", description = "El rol no es fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "La sesión no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    TherapySessionResponse find(@PathVariable Long sessionId) {
        return mapper.toResponse(sessionService.find(sessionId));
    }

    @PutMapping(path = "/api/therapy-sessions/{sessionId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Corregir una sesión",
            description = "Reemplaza los campos editables; el correlativo y el responsable no cambian, y Hibernate "
                    + "Envers conserva la versión anterior. Solo la corrige el fisioterapeuta que la registró. Enviar "
                    + "los mismos datos no cambia nada; una corrección queda auditada como UPDATE.")
    @ApiResponse(responseCode = "200", description = "Sesión corregida")
    @ApiResponse(responseCode = "400", description = "Campo obligatorio vacío, EVA fuera de 0 a 10 o fecha futura",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es fisioterapeuta, o la sesión la registró otro "
            + "fisioterapeuta (auditado como ACCESS_DENIED)", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "La sesión no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "La fecha es anterior al inicio del tratamiento", content =
            @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    TherapySessionResponse correct(@PathVariable Long sessionId, @Valid @RequestBody TherapySessionRequest request) {
        return mapper.toResponse(sessionService.correct(sessionId, request.toData()));
    }

    @GetMapping(path = "/api/therapy-sessions/{sessionId}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Versiones de una sesión",
            description = "Cada versión guardada por Hibernate Envers, de la más reciente a la más antigua, con el "
                    + "usuario y la fecha de la revisión. Queda auditada como READ de la sesión.")
    @ApiResponse(responseCode = "200", description = "Versiones de la sesión")
    @ApiResponse(responseCode = "403", description = "El rol no es fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "La sesión no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    List<TherapySessionVersionResponse> versions(@PathVariable Long sessionId) {
        return mapper.toResponses(sessionService.versions(sessionId));
    }

}
