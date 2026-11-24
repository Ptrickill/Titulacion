package ec.rehabelite.staff.web;

import ec.rehabelite.catalogs.AddressRequest;
import ec.rehabelite.shared.domain.Sex;
import ec.rehabelite.shared.web.ValidNationalId;
import ec.rehabelite.shared.web.ValidPhone;
import ec.rehabelite.staff.domain.StaffPersonalData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * New personal data of a staff member (MEJ-02). Every field is required, also for a record still to be completed.
 * The national ID can only be set when the record has none; the role is not edited here.
 */
@Schema(description = "Datos personales actualizados del miembro del personal (el rol no cambia aquí)")
record UpdateStaffRequest(
        @Schema(description = "Cédula; debe ser la registrada, o la nueva si el registro aún no tenía",
                example = "1710034065")
        @NotBlank @ValidNationalId String nationalId,

        @Schema(description = "Nombres", example = "Lucía Fernanda") @NotBlank @Size(max = 100) String firstName,

        @Schema(description = "Apellidos", example = "Mora Paz") @NotBlank @Size(max = 100) String lastName,

        @Schema(description = "Fecha de nacimiento", example = "1990-06-21") @NotNull @Past LocalDate birthDate,

        @Schema(description = "Sexo", example = "FEMALE") @NotNull Sex sex,

        @Schema(description = "Teléfono", example = "0987654321") @NotBlank @ValidPhone String phone,

        @Schema(description = "Correo electrónico; si cambia, la cuenta debe confirmarlo", example = "lucia.mora@correo.ec")
        @NotBlank @Email @Size(max = 254) String email,

        @Schema(description = "Dirección estructurada") @NotNull @Valid AddressRequest address) {

    StaffPersonalData toPersonalData() {
        return new StaffPersonalData(firstName, lastName, birthDate, sex, phone, email, address.toAddress());
    }

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "UpdateStaffRequest[...]";
    }

}
