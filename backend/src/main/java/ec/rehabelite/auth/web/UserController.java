package ec.rehabelite.auth.web;

import ec.rehabelite.auth.application.UserManagementService;
import ec.rehabelite.auth.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Gestión de cuentas y roles (solo administrador)")
class UserController {

    private final UserManagementService userManagementService;
    private final UserWebMapper mapper;

    @GetMapping
    @Operation(summary = "Listar usuarios", description = "Todas las cuentas ordenadas por nombre.")
    @ApiResponse(responseCode = "200", description = "Listado de usuarios")
    @ApiResponse(responseCode = "403", description = "El rol no es administrador", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    List<UserResponse> findAll() {
        return mapper.toResponses(userManagementService.findAll());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear usuario",
            description = "Crea una cuenta activa con el rol indicado y la contraseña cifrada con BCrypt. "
                    + "Una cuenta con rol PATIENT debe vincularse a un paciente registrado que aún no tenga cuenta.")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "400", description = "Datos con formato no válido", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El paciente indicado no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = "Correo ya registrado, rol Paciente sin paciente vinculado o paciente con cuenta",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userManagementService.create(request.toCommand());
        return ResponseEntity.created(URI.create("/api/users/" + user.getId())).body(mapper.toResponse(user));
    }

    @PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Activar o desactivar usuario",
            description = "Una cuenta desactivada no puede iniciar sesión y sus tokens emitidos dejan de ser válidos. "
                    + "Reactivarla reinicia los intentos fallidos y el bloqueo. Cada cambio queda auditado como "
                    + "STATUS_CHANGE; pedir el estado que la cuenta ya tiene no cambia nada.")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "400", description = "Falta el estado o tiene formato no válido", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es administrador", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El usuario no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "El administrador intenta desactivar su propia cuenta",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    UserResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeUserStatusRequest request) {
        return mapper.toResponse(userManagementService.changeStatus(id, request.active()));
    }

}
