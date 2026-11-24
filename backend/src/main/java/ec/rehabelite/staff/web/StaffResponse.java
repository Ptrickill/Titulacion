package ec.rehabelite.staff.web;

import ec.rehabelite.auth.Role;
import ec.rehabelite.catalogs.AddressView;
import ec.rehabelite.shared.domain.Sex;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

/** Personal data of a staff member and its account (MEJ-02). */
@Schema(description = "Datos personales del miembro del personal y su cuenta")
record StaffResponse(
        @Schema(example = "4") Long id,
        @Schema(description = "Cédula; null si el registro aún no la tiene", example = "1710034065") String nationalId,
        @Schema(description = "Nombres", example = "Lucía Fernanda") String firstName,
        @Schema(description = "Apellidos", example = "Mora Paz") String lastName,
        @Schema(description = "Fecha de nacimiento; null si falta", example = "1990-06-21") LocalDate birthDate,
        @Schema(description = "Edad en años cumplidos; null sin fecha de nacimiento", example = "36") Integer age,
        @Schema(description = "Sexo; null si falta", example = "FEMALE") Sex sex,
        @Schema(description = "Teléfono; null si falta", example = "0987654321") String phone,
        @Schema(description = "Correo electrónico", example = "lucia.mora@correo.ec") String email,
        @Schema(description = "Dirección estructurada") AddressView address,
        @Schema(description = "Rol de la cuenta", example = "PHYSIOTHERAPIST") Role role,
        @Schema(description = "Cuenta de la persona") AccountResponse account,
        @Schema(description = "Datos por completar: cédula, nacimiento, sexo, dirección o teléfono faltantes o fuera "
                + "de la regla", example = "false") boolean needsCompletion,
        @Schema(description = "Fecha y hora de registro") Instant createdAt,
        @Schema(description = "Fecha y hora de la última actualización; null si nunca se actualizó")
        Instant updatedAt) {

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "StaffResponse[id=" + id + "]";
    }

}
