package ec.rehabelite.auth.web;

import ec.rehabelite.auth.application.AccountAccessService;
import ec.rehabelite.auth.application.AuthService;
import ec.rehabelite.auth.application.InvalidCredentialsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación",
        description = "Inicio de sesión, activación de cuentas y restablecimiento de contraseña")
class AuthController {

    static final String LOGIN_FAILED_TITLE = "Inicio de sesión fallido";

    private final AuthService authService;
    private final AccountAccessService accountAccessService;

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión",
            description = "Valida el correo y la contraseña y devuelve un JWT con el id, correo, nombre completo y rol "
                    + "del usuario. Tras 5 intentos fallidos consecutivos la cuenta se bloquea 15 minutos. "
                    + "Las credenciales inválidas, la cuenta bloqueada, la cuenta inactiva y la cuenta pendiente de "
                    + "activación reciben el mismo mensaje.")
    @ApiResponse(responseCode = "200", description = "Inicio de sesión correcto")
    @ApiResponse(responseCode = "400", description = "Datos con formato no válido",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas o cuenta bloqueada",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = """
                            {
                              "title": "Inicio de sesión fallido",
                              "status": 401,
                              "detail": "Correo o contraseña incorrectos, o la cuenta está bloqueada temporalmente.",
                              "instance": "/api/auth/login"
                            }""")))
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(authService.login(request.email(), request.password()));
    }

    @PostMapping(path = "/activate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    @Operation(summary = "Activar la cuenta",
            description = "Público. Con el enlace del correo de activación, define la primera contraseña (de 8 a 72 "
                    + "caracteres, con al menos una letra y un número) y marca el correo como verificado. El enlace "
                    + "vence en 48 horas y sirve una sola vez; uno nuevo reemplaza a los anteriores. Queda auditado "
                    + "como ACTIVATION.")
    @ApiResponse(responseCode = "204", description = "Cuenta activada; ya puede iniciar sesión")
    @ApiResponse(responseCode = "400", description = "Contraseña que no cumple las reglas, confirmación distinta, o "
            + "enlace inexistente, usado, reemplazado o vencido (mismo mensaje en todos los casos del enlace)",
            content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    void activate(@Valid @RequestBody SetPasswordRequest request) {
        accountAccessService.activate(request.token(), request.password(), request.passwordConfirmation());
    }

    @PostMapping(path = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirements
    @Operation(summary = "Solicitar el restablecimiento de la contraseña",
            description = "Público. Responde siempre 202 con el mismo mensaje, exista o no el correo. Solo envía el "
                    + "enlace (válido 1 hora) si la cuenta existe, está habilitada y activada, y recibió menos de 3 "
                    + "enlaces en la última hora. Cada solicitud queda auditada como RESET_REQUEST, sin el correo.")
    @ApiResponse(responseCode = "202", description = "Solicitud recibida")
    @ApiResponse(responseCode = "400", description = "Correo con formato no válido", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accountAccessService.requestPasswordReset(request.email());
        return new MessageResponse(AccountAccessService.RESET_REQUESTED);
    }

    @PostMapping(path = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    @Operation(summary = "Restablecer la contraseña",
            description = "Público. Con el enlace del correo de restablecimiento, define una contraseña nueva con las "
                    + "mismas reglas que la activación. Reinicia los intentos fallidos y el bloqueo, y cierra las "
                    + "sesiones abiertas: los tokens emitidos antes dejan de ser válidos. Queda auditado como "
                    + "PASSWORD_RESET.")
    @ApiResponse(responseCode = "204", description = "Contraseña restablecida; ya puede iniciar sesión")
    @ApiResponse(responseCode = "400", description = "Contraseña que no cumple las reglas, confirmación distinta, o "
            + "enlace inexistente, usado, reemplazado o vencido (mismo mensaje en todos los casos del enlace)",
            content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    void resetPassword(@Valid @RequestBody SetPasswordRequest request) {
        accountAccessService.resetPassword(request.token(), request.password(), request.passwordConfirmation());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle(LOGIN_FAILED_TITLE);
        return problem;
    }

}
