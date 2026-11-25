package ec.rehabelite.staff.web;

import ec.rehabelite.catalogs.AddressFormatter;
import ec.rehabelite.shared.config.AppProperties;
import ec.rehabelite.shared.domain.ClinicCalendar;
import ec.rehabelite.shared.web.PageResponse;
import ec.rehabelite.staff.application.PeopleRegistryService;
import ec.rehabelite.staff.application.PeopleRegistryService.AccountFilter;
import ec.rehabelite.staff.application.PeopleRegistryService.PersonType;
import ec.rehabelite.staff.application.StaffRegistrationService;
import ec.rehabelite.staff.application.StaffScheduleService;
import ec.rehabelite.staff.domain.StaffRecord;
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
import lombok.RequiredArgsConstructor;
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
@RequestMapping(path = "/api/staff", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Personal", description = "Gestión del personal y de sus cuentas (MEJ-02, MEJ-05, solo dirección "
        + "operativa) y horario de los fisioterapeutas (MEJ-04: lo edita la dirección; la recepción lo consulta)")
class StaffController {

    /** Status of a staff account: every staff member has one, so there is no NONE. Any other value answers 400. */
    enum StaffAccountStatusParam {
        PENDING_ACTIVATION,
        ACTIVE,
        INACTIVE
    }

    private final StaffRegistrationService registrationService;
    private final StaffScheduleService scheduleService;
    private final PeopleRegistryService registryService;
    private final AppProperties properties;
    private final StaffWebMapper mapper;
    private final AddressFormatter addressFormatter;
    private final Clock clock;

    @GetMapping
    @Operation(summary = "Listar el personal",
            description = "Fisioterapeutas, recepción y dirección operativa con el estado de su cuenta y el último "
                    + "acceso, por apellidos y nombres (MEJ-05). Dígitos buscan el inicio de la cédula; si no, cada "
                    + "palabra debe aparecer en los nombres. Datos personales: no se audita.")
    @ApiResponse(responseCode = "200", description = "Página del personal")
    @ApiResponse(responseCode = "400", description = "Filtro no válido o página fuera de rango", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es dirección operativa", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    PageResponse<PersonResponse> list(
            @Parameter(description = "Nombres, apellidos o inicio de la cédula", example = "andrade")
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @Parameter(description = "Solo el personal con ese rol") @RequestParam(required = false) StaffRoleParam role,
            @Parameter(description = "Solo el personal cuya cuenta tiene ese estado")
            @RequestParam(required = false) StaffAccountStatusParam accountStatus,
            @Parameter(description = "Número de página, desde 0", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamaño de página (1 a 50)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return PeopleController.toPage(registryService.list(q, PersonType.STAFF, role == null ? null : role.role(),
                accountStatus == null ? null : AccountFilter.valueOf(accountStatus.name()), page, size), page, size);
    }

    @GetMapping("/stats")
    @Operation(summary = "Indicadores del personal",
            description = "Cuentas activas del personal, y cuentas de fisioterapeutas, de recepción y de dirección "
                    + "operativa en cualquier estado. Una sola consulta. No se audita.")
    @ApiResponse(responseCode = "200", description = "Indicadores")
    StaffStatsResponse stats() {
        return StaffStatsResponse.of(registryService.staffStatistics());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar miembro del personal",
            description = "Registra los datos personales de un fisioterapeuta, recepcionista o director operativo y "
                    + "crea su cuenta con ese correo y sus nombres, pendiente de activación, en una sola operación; "
                    + "al final envía el enlace \"Active su cuenta de Rehab Elite\" (vence en 48 horas). Si el correo "
                    + "no se puede enviar, no se crea nada. La cédula es única entre pacientes y personal, y el correo "
                    + "es único frente a pacientes, personal y cuentas. Queda auditado como CREATE User, CREATE "
                    + "StaffRecord e INVITATION_SENT User.")
    @ApiResponse(responseCode = "201", description = "Registro y cuenta creados, enlace de activación enviado")
    @ApiResponse(responseCode = "400", description = "Datos no válidos (p. ej. rol PATIENT o cédula incorrecta)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es dirección operativa", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "La cédula o el correo ya están registrados; existingPatientId o "
            + "existingStaffId y field indican cuál", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "No se pudo enviar el correo de activación; no se creó nada",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<StaffResponse> register(@Valid @RequestBody RegisterStaffRequest request) {
        StaffRecord staff = registrationService.register(request.toCommand());
        return ResponseEntity.created(URI.create("/api/staff/" + staff.getId())).body(toResponse(staff));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar miembro del personal",
            description = "Datos personales, rol y estado de la cuenta. Datos personales: no se audita.")
    @ApiResponse(responseCode = "200", description = "Datos del miembro del personal")
    @ApiResponse(responseCode = "404", description = "El registro no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    StaffResponse findById(@PathVariable Long id) {
        return toResponse(registrationService.find(id));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualizar miembro del personal",
            description = "Reemplaza los datos personales; todos son obligatorios, también para un registro con "
                    + "datos por completar. La cédula registrada no cambia (un registro sin cédula la recibe aquí) y "
                    + "el rol no se edita. La cuenta toma los nombres del registro; un correo nuevo envía un enlace "
                    + "de confirmación al nuevo correo y un aviso sin enlace al anterior, y hasta confirmarlo la cuenta "
                    + "sigue con el correo anterior. Queda auditado como UPDATE StaffRecord (solo si cambia algo) y "
                    + "EMAIL_CHANGE User.")
    @ApiResponse(responseCode = "200", description = "Datos actualizados")
    @ApiResponse(responseCode = "400", description = "Datos no válidos", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El registro no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "La cédula o el correo ya están registrados", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Cédula distinta de la registrada, o cambio de correo de una "
            + "cuenta desactivada", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "No se pudo enviar el enlace al nuevo correo; no cambió nada",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    StaffResponse update(@PathVariable Long id, @Valid @RequestBody UpdateStaffRequest request) {
        return toResponse(registrationService.update(id, request.nationalId(), request.toPersonalData()));
    }

    @GetMapping("/{id}/schedule")
    @Operation(summary = "Consultar horario de un fisioterapeuta",
            description = "Franjas semanales (lunes a sábado) del fisioterapeuta, ordenadas por día y hora, con la "
                    + "jornada de la clínica. Recepción (solo lectura) y dirección operativa (MEJ-04, MEJ-05). No se "
                    + "audita.")
    @ApiResponse(responseCode = "200", description = "Horario del fisioterapeuta")
    @ApiResponse(responseCode = "403", description = "El rol no es recepción ni dirección operativa",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El registro no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "El registro no es de un fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ScheduleResponse schedule(@PathVariable Long id) {
        return ScheduleResponse.of(scheduleService.schedule(id), properties.clinic());
    }

    @PutMapping(path = "/{id}/schedule", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reemplazar horario de un fisioterapeuta",
            description = "Reemplaza la semana completa con la lista enviada (una lista vacía deja al fisioterapeuta "
                    + "sin horario). Cada franja va de lunes a sábado, en horas y minutos, dentro de la jornada de la "
                    + "clínica, y no se superpone con otra del mismo día; las franjas contiguas se unen (08:00-12:00 y "
                    + "12:00-14:00 quedan como 08:00-14:00). Solo la dirección operativa (MEJ-05). Queda auditado como "
                    + "UPDATE StaffRecord, solo si cambia algo.")
    @ApiResponse(responseCode = "200", description = "Horario guardado, con las franjas contiguas unidas")
    @ApiResponse(responseCode = "400", description = "Franja fuera de la jornada, superpuesta, en domingo, con "
            + "segundos o con la hora de inicio posterior a la de fin", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es dirección operativa",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El registro no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "El registro no es de un fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ScheduleResponse replaceSchedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest request) {
        return ScheduleResponse.of(scheduleService.replace(id, request.toSlots()), properties.clinic());
    }

    private StaffResponse toResponse(StaffRecord staff) {
        return mapper.toResponse(staff, ClinicCalendar.today(clock), addressFormatter,
                registrationService.accountOf(staff).orElse(null));
    }

}
