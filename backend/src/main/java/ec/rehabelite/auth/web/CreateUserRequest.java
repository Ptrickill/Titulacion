package ec.rehabelite.auth.web;

import ec.rehabelite.auth.Role;
import ec.rehabelite.auth.application.CreateUserCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos de la nueva cuenta de usuario. No lleva contraseña: la define su titular con el enlace "
        + "de activación que recibe por correo")
record CreateUserRequest(
        @Schema(description = "Correo electrónico (se guarda en minúsculas)", example = "lucia.mora@rehabelite.ec")
        @NotBlank @Email @Size(max = 254) String email,

        @Schema(description = "Nombres y apellidos", example = "Lucía Fernanda Mora Paz")
        @NotBlank @Size(max = 150) String fullName,

        @Schema(description = "Rol asignado", example = "PHYSIOTHERAPIST")
        @NotNull Role role,

        @Schema(description = "Paciente vinculado; obligatorio solo para el rol PATIENT", example = "15")
        @Positive Long patientId) {

    CreateUserCommand toCommand() {
        return new CreateUserCommand(email, fullName, role, patientId);
    }

}
