package ec.rehabelite.patients.web;

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

/**
 * New admission date and personal data. There is no national ID or medical record number: they never change, and
 * if a client sends them they are ignored. Same rules as {@link RegisterPatientRequest}.
 */
@Schema(description = "Datos de admisión y personales actualizados (la cédula y el número de historia no cambian)")
record UpdatePatientRequest(
        @Schema(description = "Fecha de ingreso", example = "2026-10-29")
        @NotNull @PastOrPresent LocalDate admissionDate,

        @Schema(description = "Nombres", example = "Juan Carlos")
        @NotBlank @Size(max = 100) String firstName,

        @Schema(description = "Apellidos", example = "Pérez Gómez")
        @NotBlank @Size(max = 100) String lastName,

        @Schema(description = "Fecha de nacimiento", example = "1996-03-15")
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

    PersonalData toPersonalData() {
        return new PersonalData(firstName, lastName, birthDate, sex, phone, email, address, occupation, educationLevel,
                healthInsurance, ethnicSelfIdentification, religion);
    }

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "UpdatePatientRequest[...]";
    }

}
