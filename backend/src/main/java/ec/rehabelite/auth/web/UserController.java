package ec.rehabelite.auth.web;

import ec.rehabelite.auth.application.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Acciones sobre las cuentas (MEJ-05): la recepción, sobre las cuentas del portal "
        + "de los pacientes; la dirección operativa, sobre las cuentas del personal")
class UserController {

    private final UserManagementService userManagementService;
    private final UserWebMapper mapper;


    @PostMapping("/{id}/resend-invitation")
    @Operation(summary = "Reenviar la invitación",
            description = "Envía un enlace nuevo a una cuenta habilitada que espera a su titular: el de activación si "
                    + "está pendiente de activación, o el de confirmación del nuevo correo si tiene un cambio de "
                    + "correo por confirmar (MEJ-02). Los enlaces anteriores de ese tipo dejan de servir. Queda "
                    + "auditado como INVITATION_RESENT. La recepción reenvía solo a cuentas de pacientes y la dirección "
                    + "operativa solo a cuentas del personal (MEJ-05). Las cuentas se crean desde los registros "
                    + "(POST /api/staff y POST /api/patients/{id}/portal-account), nunca escribiendo el nombre o el "
                    + "correo.")
    @ApiResponse(responseCode = "200", description = "Enlace reenviado")
    @ApiResponse(responseCode = "403", description = "El rol no es recepción ni dirección operativa, o la cuenta "
            + "es del otro lado (MEJ-05)", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El usuario no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "La cuenta está desactivada o no espera ningún enlace",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "No se pudo enviar el correo; los enlaces anteriores siguen vigentes",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    UserResponse resendInvitation(@PathVariable Long id) {
        return mapper.toResponse(userManagementService.resendInvitation(id));
    }

    @PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Activar o desactivar usuario",
            description = "Una cuenta desactivada no puede iniciar sesión y sus tokens emitidos dejan de ser válidos. "
                    + "Reactivarla reinicia los intentos fallidos y el bloqueo. Cada cambio queda auditado como "
                    + "STATUS_CHANGE; pedir el estado que la cuenta ya tiene no cambia nada. La recepción administra "
                    + "solo cuentas de pacientes y la dirección operativa solo cuentas del personal; siempre queda al "
                    + "menos una cuenta de dirección operativa activa (MEJ-05).")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "400", description = "Falta el estado o tiene formato no válido", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es recepción ni dirección operativa, o la cuenta "
            + "es del otro lado (MEJ-05)", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El usuario no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Intenta desactivar su propia cuenta o la última cuenta activa de "
            + "dirección operativa",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    UserResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeUserStatusRequest request) {
        return mapper.toResponse(userManagementService.changeStatus(id, request.active()));
    }

}
