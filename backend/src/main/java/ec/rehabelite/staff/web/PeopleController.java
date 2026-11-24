package ec.rehabelite.staff.web;

import ec.rehabelite.shared.web.PageResponse;
import ec.rehabelite.staff.application.PeopleRegistryService;
import ec.rehabelite.staff.application.PeopleRegistryService.PeoplePage;
import ec.rehabelite.staff.application.PeopleRegistryService.PersonType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/people", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Registro de Usuarios", description = "Pacientes y personal en una sola lista (MEJ-02, solo administrador)")
class PeopleController {

    private final PeopleRegistryService registryService;

    @GetMapping
    @Operation(summary = "Listar personas",
            description = "Pacientes y personal con el estado de su cuenta: NONE (sin cuenta), PENDING_ACTIVATION, "
                    + "ACTIVE o INACTIVE. Primero el personal y luego los pacientes, cada grupo por apellidos y "
                    + "nombres. La búsqueda es la de pacientes: dígitos buscan el inicio de la cédula; si no, cada "
                    + "palabra debe aparecer en los nombres. Datos personales: no se audita.")
    @ApiResponse(responseCode = "200", description = "Página de personas")
    @ApiResponse(responseCode = "400", description = "Filtro no válido (p. ej. rol con el tipo PATIENT) o página fuera "
            + "de rango", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    PageResponse<PersonResponse> list(
            @Parameter(description = "Nombres, apellidos o inicio de la cédula", example = "mora")
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @Parameter(description = "Solo pacientes (PATIENT) o solo personal (STAFF); sin valor, ambos")
            @RequestParam(required = false) PersonType type,
            @Parameter(description = "Solo el personal con ese rol")
            @RequestParam(required = false) StaffRoleParam role,
            @Parameter(description = "Número de página, desde 0", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamaño de página (1 a 50)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        PeoplePage result = registryService.list(q, type, role == null ? null : role.role(), page, size);
        long totalPages = (result.total() + size - 1) / size;
        return new PageResponse<>(result.content().stream().map(PersonResponse::of).toList(), page, size,
                result.total(), (int) totalPages);
    }

    @GetMapping("/matches")
    @Operation(summary = "Buscar cédula o correo registrados",
            description = "Pacientes y personal que ya tienen la cédula o el correo (en su registro o en su cuenta, "
                    + "también como correo nuevo por confirmar). El formulario del registro lo usa para impedir un "
                    + "duplicado o, entre pacientes que comparten correo, para avisar. No se audita.")
    @ApiResponse(responseCode = "200", description = "Personas que coinciden; lista vacía si ninguna")
    List<PersonMatchResponse> matches(
            @Parameter(description = "Cédula", example = "1710034065")
            @RequestParam(required = false) @Size(max = 10) String nationalId,
            @Parameter(description = "Correo electrónico", example = "lucia.mora@correo.ec")
            @RequestParam(required = false) @Size(max = 254) String email) {
        return registryService.matches(nationalId, email).stream().map(PersonMatchResponse::of).toList();
    }

}
