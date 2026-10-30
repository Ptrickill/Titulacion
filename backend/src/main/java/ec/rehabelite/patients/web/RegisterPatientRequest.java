package ec.rehabelite.patients.web;

import ec.rehabelite.patients.application.RegisterPatientCommand;
import ec.rehabelite.patients.domain.PersonalData;
import ec.rehabelite.patients.domain.Sex;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Admission and personal data only: no clinical data, and no medical record number (the server assigns it). */
@Schema(description = "Datos de admisión y datos personales del nuevo paciente")
record RegisterPatientRequest(
        @Schema(description = "Cédula ecuatoriana; también será el número de historia clínica", example = "1710034065")
        @NotBlank @ValidNationalId String nationalId,

        @Schema(description = "Fecha de ingreso; si se omite, la fecha de hoy", example = "2026-10-29")
        @PastOrPresent LocalDate admissionDate,

        @Schema(description = "Nombres", example = "Juan Carlos")
        @NotBlank @Size(max = 100) String firstName,

        @Schema(description = "Apellidos", example = "Pérez Gómez")
        @NotBlank @Size(max = 100) String lastName,

        @Schema(description = "Fecha de nacimiento; la edad se calcula a partir de ella", example = "1996-03-15")
        @NotNull @Past LocalDate birthDate,

        @Schema(description = "Sexo", example = "MALE")
        @NotNull Sex sex,

        @Schema(description = "Teléfono", example = "+593 98 765 4321")
        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^\\+?[0-9][0-9 ]{5,18}$", message = "debe contener solo números, espacios y el prefijo +")
        String phone,

        @Schema(description = "Correo electrónico", example = "juan.perez@correo.ec")
        @NotBlank @Email @Size(max = 254) String email,

        @Schema(description = "Dirección", example = "Av. Ilaló y Río Pita, Valle de los Chillos")
        @NotBlank @Size(max = 255) String address,

        @Schema(description = "Ocupación", example = "Ingeniero civil")
        @NotBlank @Size(max = 100) String occupation,

        @Schema(description = "Nivel de instrucción", example = "Superior")
        @NotBlank @Size(max = 50) String educationLevel,

        @Schema(description = "Seguro de salud", example = "IESS")
        @NotBlank @Size(max = 100) String healthInsurance,

        @Schema(description = "Autoidentificación étnica (opcional)", example = "Mestizo")
        @Size(max = 50) String ethnicSelfIdentification,

        @Schema(description = "Religión (opcional)", example = "Católica")
        @Size(max = 50) String religion) {

    RegisterPatientCommand toCommand() {
        return new RegisterPatientCommand(nationalId, admissionDate, new PersonalData(firstName, lastName,
                birthDate, sex, phone, email, address, occupation, educationLevel, healthInsurance,
                ethnicSelfIdentification, religion));
    }

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "RegisterPatientRequest[...]";
    }

}
