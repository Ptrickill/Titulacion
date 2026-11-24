package ec.rehabelite.staff.web;

import ec.rehabelite.catalogs.AddressRequest;
import ec.rehabelite.shared.domain.Sex;
import ec.rehabelite.shared.web.ValidNationalId;
import ec.rehabelite.shared.web.ValidPhone;
import ec.rehabelite.staff.application.RegisterStaffCommand;
import ec.rehabelite.staff.domain.StaffPersonalData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** A new staff member and the role of its account (MEJ-02): the same personal data as a patient. */
@Schema(description = "Datos personales del nuevo miembro del personal y el rol de su cuenta")
record RegisterStaffRequest(
        @Schema(description = "Rol de la cuenta", example = "PHYSIOTHERAPIST") @NotNull StaffRoleParam role,

        @Schema(description = "Cédula ecuatoriana", example = "1710034065")
        @NotBlank @ValidNationalId String nationalId,

        @Schema(description = "Nombres", example = "Lucía Fernanda") @NotBlank @Size(max = 100) String firstName,

        @Schema(description = "Apellidos", example = "Mora Paz") @NotBlank @Size(max = 100) String lastName,

        @Schema(description = "Fecha de nacimiento", example = "1990-06-21") @NotNull @Past LocalDate birthDate,

        @Schema(description = "Sexo", example = "FEMALE") @NotNull Sex sex,

        @Schema(description = "Teléfono: celular 09 y 8 dígitos, o fijo 0, código de provincia (2 a 7) y 7 dígitos",
                example = "0987654321")
        @NotBlank @ValidPhone String phone,

        @Schema(description = "Correo electrónico; recibirá el enlace de activación", example = "lucia.mora@correo.ec")
        @NotBlank @Email @Size(max = 254) String email,

        @Schema(description = "Dirección estructurada") @NotNull @Valid AddressRequest address) {

    RegisterStaffCommand toCommand() {
        return new RegisterStaffCommand(role.role(), nationalId,
                new StaffPersonalData(firstName, lastName, birthDate, sex, phone, email, address.toAddress()));
    }

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "RegisterStaffRequest[role=" + role + "]";
    }

}
