package ec.rehabelite.staff.web;

import ec.rehabelite.shared.web.PageResponse;
import ec.rehabelite.staff.application.PeopleRegistryService;
import ec.rehabelite.staff.application.PeopleRegistryService.AccountFilter;
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

/**
 * The reception's "Registro de Pacientes" (MEJ-05): patients only, with their portal accounts. The staff is managed
 * by the operations director from /api/staff. The lookup of national IDs and emails serves both registration forms.
 */
@RestController
@RequestMapping(path = "/api/people", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Registro de Pacientes", description = "Pacientes y su acceso al portal (MEJ-05, solo recepción)")
class PeopleController {

    private final PeopleRegistryService registryService;

    @GetMapping
    @Operation(summary = "Listar pacientes",
            description = "Pacientes con el estado de su acceso al portal: NONE (sin cuenta), PENDING_ACTIVATION, ACTIVE "
                    + "o INACTIVE, y el último acceso, por apellidos y nombres. Dígitos buscan el inicio de la cédula; "
                    + "si no, cada palabra debe aparecer en los nombres. Nunca el personal (MEJ-05). Datos personales: "
                    + "no se audita.")
    @ApiResponse(responseCode = "200", description = "Página de pacientes")
    @ApiResponse(responseCode = "400", description = "Filtro no válido o página fuera de rango", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es recepción", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    PageResponse<PersonResponse> list(
            @Parameter(description = "Nombres, apellidos o inicio de la cédula", example = "mora")
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @Parameter(description = "Solo los pacientes cuyo acceso al portal tiene ese estado, o NONE para los que no "
                    + "tienen cuenta")
            @RequestParam(required = false) AccountFilter accountStatus,
            @Parameter(description = "Número de página, desde 0", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamaño de página (1 a 50)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return toPage(registryService.list(q, PersonType.PATIENT, null, accountStatus, page, size), page, size);
    }

    @GetMapping("/stats")
    @Operation(summary = "Indicadores del registro de pacientes",
            description = "Pacientes registrados, accesos al portal activos y pendientes de activación, y pacientes sin "
                    + "acceso al portal. No se audita.")
    @ApiResponse(responseCode = "200", description = "Indicadores")
    PeopleStatsResponse stats() {
        return PeopleStatsResponse.of(registryService.patientStatistics());
    }

    @GetMapping("/matches")
    @Operation(summary = "Buscar cédula o correo registrados",
            description = "Pacientes y personal que ya tienen la cédula o el correo (en su registro o en su cuenta, "
                    + "también como correo nuevo por confirmar). Los formularios de la recepción y de la dirección lo "
                    + "usan para impedir un duplicado o, entre pacientes que comparten correo, para avisar. No se "
                    + "audita.")
    @ApiResponse(responseCode = "200", description = "Personas que coinciden; lista vacía si ninguna")
    List<PersonMatchResponse> matches(
            @Parameter(description = "Cédula", example = "1710034065")
            @RequestParam(required = false) @Size(max = 10) String nationalId,
            @Parameter(description = "Correo electrónico", example = "lucia.mora@correo.ec")
            @RequestParam(required = false) @Size(max = 254) String email) {
        return registryService.matches(nationalId, email).stream().map(PersonMatchResponse::of).toList();
    }

    static PageResponse<PersonResponse> toPage(PeoplePage result, int page, int size) {
        long totalPages = (result.total() + size - 1) / size;
        return new PageResponse<>(result.content().stream().map(PersonResponse::of).toList(), page, size,
                result.total(), (int) totalPages);
    }

}
