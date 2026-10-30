package ec.rehabelite.auth.web;

import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.CreateUserCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos de la nueva cuenta de usuario")
record CreateUserRequest(
        @Schema(description = "Correo electrónico (se guarda en minúsculas)", example = "lucia.mora@rehabelite.ec")
        @NotBlank @Email @Size(max = 254) String email,

        @Schema(description = "Nombres y apellidos", example = "Lucía Fernanda Mora Paz")
        @NotBlank @Size(max = 150) String fullName,

        @Schema(description = "Rol asignado", example = "PHYSIOTHERAPIST")
        @NotNull Role role,

        @Schema(description = "Contraseña inicial: de 8 a 72 caracteres, con al menos una letra y un número",
                example = "Clave.Segura2026", format = "password")
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$",
                message = "debe contener al menos una letra y un número")
        String password,

        @Schema(description = "Paciente vinculado; obligatorio solo para el rol PATIENT", example = "15")
        @Positive Long patientId) {

    CreateUserCommand toCommand() {
        return new CreateUserCommand(email, fullName, role, password, patientId);
    }

    /** Never print the password, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "CreateUserRequest[role=" + role + ", patientId=" + patientId + "]";
    }

}
