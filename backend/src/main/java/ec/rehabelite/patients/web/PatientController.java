package ec.rehabelite.patients.web;

import ec.rehabelite.patients.application.PatientManagementService;
import ec.rehabelite.patients.application.PatientRegistrationService;
import ec.rehabelite.patients.application.PortalAccountService;
import ec.rehabelite.patients.domain.Patient;
import ec.rehabelite.shared.domain.ClinicCalendar;
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
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/patients", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Pacientes", description = "Datos personales de los pacientes")
class PatientController {

    private final PatientRegistrationService registrationService;
    private final PatientManagementService managementService;
    private final PortalAccountService portalAccountService;
    private final PatientWebMapper mapper;
    private final Clock clock;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar paciente",
            description = "Registra los datos de admisión y personales de un paciente nuevo (sin datos clínicos). "
                    + "El número de historia clínica es la cédula y lo asigna el servidor; la edad se calcula desde "
                    + "la fecha de nacimiento y no se almacena. Queda auditado como CREATE. Solo administrador.")
    @ApiResponse(responseCode = "201", description = "Paciente registrado")
    @ApiResponse(responseCode = "400", description = "Datos no válidos (p. ej. cédula con dígito verificador "
            + "incorrecto)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es administrador", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Ya existe un paciente con esa cédula; la propiedad "
            + "existingPatientId indica cuál", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "La fecha de nacimiento es posterior a la fecha de ingreso",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<PatientResponse> register(@Valid @RequestBody RegisterPatientRequest request) {
        Patient patient = registrationService.register(request.toCommand());
        return ResponseEntity.created(URI.create("/api/patients/" + patient.getId()))
                .body(mapper.toResponse(patient, ClinicCalendar.today(clock)));
    }

    @GetMapping
    @Operation(summary = "Buscar pacientes",
            description = "Búsqueda parcial sin distinguir tildes ni mayúsculas: si la consulta son solo dígitos, "
                    + "busca las cédulas que empiezan así; si no, cada palabra debe aparecer en los nombres o "
                    + "apellidos. Sin consulta lista todos. Ordenado por apellidos y nombres, paginado. No incluye "
                    + "datos clínicos.")
    @ApiResponse(responseCode = "200", description = "Página de pacientes")
    @ApiResponse(responseCode = "400", description = "Página o tamaño fuera de rango", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    PageResponse<PatientSummaryResponse> search(
            @Parameter(description = "Nombres, apellidos o inicio de la cédula", example = "perez")
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @Parameter(description = "Número de página, desde 0", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamaño de página (1 a 50)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        LocalDate today = ClinicCalendar.today(clock);
        return PageResponse.of(managementService.search(q, PageRequest.of(page, size)),
                patient -> mapper.toSummary(patient, today));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar datos del paciente",
            description = "Reemplaza la fecha de ingreso y los datos personales. La cédula y el número de historia "
                    + "clínica no se modifican. Registra quién y cuándo (updated_by, updated_at) y queda auditado como "
                    + "UPDATE; enviar los mismos datos no cambia nada. Solo administrador.")
    @ApiResponse(responseCode = "200", description = "Datos actualizados")
    @ApiResponse(responseCode = "400", description = "Datos no válidos", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es administrador", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El paciente no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "La fecha de nacimiento es posterior a la fecha de ingreso",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    PatientResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePatientRequest request) {
        Patient patient = managementService.update(id, request.admissionDate(), request.toPersonalData());
        return mapper.toResponse(patient, ClinicCalendar.today(clock));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar paciente",
            description = "Datos de admisión y personales de un paciente, con la edad calculada a la fecha de hoy. "
                    + "No incluye datos clínicos.")
    @ApiResponse(responseCode = "200", description = "Datos del paciente")
    @ApiResponse(responseCode = "404", description = "El paciente no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    PatientResponse findById(@PathVariable Long id) {
        return mapper.toResponse(registrationService.findById(id), ClinicCalendar.today(clock));
    }

    @GetMapping("/portal-candidates")
    @Operation(summary = "Pacientes sin cuenta de portal",
            description = "Hasta 20 pacientes sin cuenta de portal cuyo nombre (sin distinguir tildes ni "
                    + "mayúsculas) o cédula coinciden con la búsqueda. Solo administrador.")
    @ApiResponse(responseCode = "200", description = "Pacientes que pueden recibir una cuenta de portal")
    List<PortalCandidateResponse> findPortalCandidates(
            @Parameter(description = "Nombre, apellido o inicio de la cédula", example = "ortiz")
            @RequestParam(defaultValue = "") @Size(max = 100) String query) {
        return mapper.toPortalCandidates(portalAccountService.findCandidates(query));
    }

}
