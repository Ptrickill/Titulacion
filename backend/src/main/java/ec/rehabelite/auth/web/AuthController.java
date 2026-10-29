package ec.rehabelite.auth.web;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Inicio de sesión con correo y contraseña")
class AuthController {

    static final String LOGIN_FAILED_TITLE = "Inicio de sesión fallido";

    private final AuthService authService;

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión",
            description = "Valida el correo y la contraseña y devuelve un JWT con el id, correo, nombre completo y rol "
                    + "del usuario. Tras 5 intentos fallidos consecutivos la cuenta se bloquea 15 minutos. "
                    + "Las credenciales inválidas, la cuenta bloqueada y la cuenta inactiva reciben el mismo mensaje.")
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

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle(LOGIN_FAILED_TITLE);
        return problem;
    }

}
