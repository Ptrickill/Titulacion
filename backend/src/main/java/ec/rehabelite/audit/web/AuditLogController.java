package ec.rehabelite.audit.web;

import ec.rehabelite.audit.AuditAction;
import ec.rehabelite.audit.AuditUser;
import ec.rehabelite.audit.application.AuditLogQueryService;
import ec.rehabelite.audit.application.AuditLogQueryService.AuditLogPage;
import ec.rehabelite.audit.domain.AuditLog;
import ec.rehabelite.audit.domain.AuditLogFilter;
import ec.rehabelite.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only audit log (HU-18) for the administrator and the operations director. */
@RestController
@RequestMapping(path = "/api/audit-logs", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Registro de auditoría de solo lectura (administrador y dirección operativa)")
class AuditLogController {

    static final String INVALID_RANGE = "La fecha inicial no puede ser posterior a la fecha final.";

    private final AuditLogQueryService queryService;

    @Schema(description = "Entrada del registro de auditoría: solo metadatos, nunca datos clínicos")
    record AuditLogResponse(
            Long id,
            @Schema(description = "Fecha y hora (UTC)") Instant occurredAt,
            @Schema(description = "Usuario que actuó; null en intentos anónimos") Long userId,
            @Schema(description = "Nombre del usuario; null si no hay usuario") String userName,
            AuditAction action,
            @Schema(example = "ClinicalRecord") String entityType,
            Long entityId,
            @Schema(example = "190.152.10.4") String ipAddress) {
    }

    @Schema(description = "Usuario que puede aparecer en la auditoría")
    record AuditUserResponse(Long id, String fullName) {
    }

    @GetMapping
    @Operation(summary = "Consultar la auditoría",
            description = "Entradas del registro de auditoría, de la más reciente a la más antigua, con filtros "
                    + "opcionales por usuario, acción, entidad e id, y por rango de fechas (días de Quito, ambos "
                    + "incluidos). Solo lectura: el registro no se modifica ni se elimina.")
    @ApiResponse(responseCode = "200", description = "Página de entradas")
    @ApiResponse(responseCode = "400", description = "Filtros no válidos (p. ej. fecha inicial posterior a la final)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es administrador ni dirección operativa",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    PageResponse<AuditLogResponse> search(
            @Parameter(description = "Id del usuario que actuó") @RequestParam(required = false) Long userId,
            @Parameter(description = "Acción registrada") @RequestParam(required = false) AuditAction action,
            @Parameter(description = "Entidad afectada", example = "ClinicalRecord")
            @RequestParam(required = false) @Size(max = 60) String entityType,
            @Parameter(description = "Id de la entidad afectada") @RequestParam(required = false) Long entityId,
            @Parameter(description = "Desde (día, inclusive)", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Hasta (día, inclusive)", example = "2026-10-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Número de página, desde 0", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamaño de página (1 a 100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_RANGE);
        }
        AuditLogPage result = queryService.search(
                new AuditLogFilter(userId, action, entityType, entityId, from, to), page, size);
        return PageResponse.of(result.entries(), entry -> toResponse(entry, result));
    }

    @GetMapping("/entity-types")
    @Operation(summary = "Entidades de la auditoría", description = "Tipos de entidad que aparecen en el registro, "
            + "para el filtro por entidad.")
    @ApiResponse(responseCode = "200", description = "Tipos de entidad ordenados")
    List<String> entityTypes() {
        return queryService.entityTypes();
    }

    @GetMapping("/users")
    @Operation(summary = "Usuarios de la auditoría", description = "Todas las cuentas (id y nombre), activas o no, "
            + "para el filtro por usuario.")
    @ApiResponse(responseCode = "200", description = "Usuarios ordenados por nombre")
    List<AuditUserResponse> users() {
        return queryService.users().stream().map(AuditLogController::toResponse).toList();
    }

    private static AuditLogResponse toResponse(AuditLog entry, AuditLogPage page) {
        return new AuditLogResponse(entry.getId(), entry.getOccurredAt(), entry.getUserId(),
                page.nameOf(entry.getUserId()), entry.getAction(), entry.getEntityType(), entry.getEntityId(),
                entry.getIpAddress());
    }

    private static AuditUserResponse toResponse(AuditUser user) {
        return new AuditUserResponse(user.id(), user.fullName());
    }

}
