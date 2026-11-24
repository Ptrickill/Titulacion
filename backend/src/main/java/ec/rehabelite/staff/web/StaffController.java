package ec.rehabelite.staff.web;

import ec.rehabelite.catalogs.AddressFormatter;
import ec.rehabelite.shared.config.AppProperties;
import ec.rehabelite.shared.domain.ClinicCalendar;
import ec.rehabelite.staff.application.StaffRegistrationService;
import ec.rehabelite.staff.application.StaffScheduleService;
import ec.rehabelite.staff.domain.StaffRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/staff", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Personal", description = "Registro del personal y su cuenta (MEJ-02, solo administrador) y horario de "
        + "los fisioterapeutas (MEJ-04, administrador y dirección operativa)")
class StaffController {

    private final StaffRegistrationService registrationService;
    private final StaffScheduleService scheduleService;
    private final AppProperties properties;
    private final StaffWebMapper mapper;
    private final AddressFormatter addressFormatter;
    private final Clock clock;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar miembro del personal",
            description = "Registra los datos personales de un fisioterapeuta, administrador o director operativo y "
                    + "crea su cuenta con ese correo y sus nombres, pendiente de activación, en una sola operación; "
                    + "al final envía el enlace \"Active su cuenta de Rehab Elite\" (vence en 48 horas). Si el correo "
                    + "no se puede enviar, no se crea nada. La cédula es única entre pacientes y personal, y el correo "
                    + "es único frente a pacientes, personal y cuentas. Queda auditado como CREATE User, CREATE "
                    + "StaffRecord e INVITATION_SENT User.")
    @ApiResponse(responseCode = "201", description = "Registro y cuenta creados, enlace de activación enviado")
    @ApiResponse(responseCode = "400", description = "Datos no válidos (p. ej. rol PATIENT o cédula incorrecta)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es administrador", content = @Content(
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
                    + "jornada de la clínica. Administrador y dirección operativa (MEJ-04). No se audita.")
    @ApiResponse(responseCode = "200", description = "Horario del fisioterapeuta")
    @ApiResponse(responseCode = "403", description = "El rol no es administrador ni dirección operativa",
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
                    + "12:00-14:00 quedan como 08:00-14:00). Administrador y dirección operativa (MEJ-04). Queda "
                    + "auditado como UPDATE StaffRecord, solo si cambia algo.")
    @ApiResponse(responseCode = "200", description = "Horario guardado, con las franjas contiguas unidas")
    @ApiResponse(responseCode = "400", description = "Franja fuera de la jornada, superpuesta, en domingo, con "
            + "segundos o con la hora de inicio posterior a la de fin", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es administrador ni dirección operativa",
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
