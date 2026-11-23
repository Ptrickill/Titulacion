package ec.rehabelite.patients.web;

import ec.rehabelite.patients.domain.Sex;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

/** Admission and personal data of a patient; never clinical data. */
@Schema(description = "Datos de admisión y datos personales del paciente")
record PatientResponse(
        @Schema(example = "15") Long id,
        @Schema(description = "Cédula", example = "1710034065") String nationalId,
        @Schema(description = "Número de historia clínica (igual a la cédula)", example = "1710034065")
        String medicalRecordNumber,
        @Schema(description = "Fecha de ingreso", example = "2026-10-29") LocalDate admissionDate,
        @Schema(description = "Nombres", example = "Juan Carlos") String firstName,
        @Schema(description = "Apellidos", example = "Pérez Gómez") String lastName,
        @Schema(description = "Fecha de nacimiento", example = "1996-03-15") LocalDate birthDate,
        @Schema(description = "Edad en años cumplidos, calculada desde la fecha de nacimiento (no se almacena)",
                example = "30")
        int age,
        @Schema(description = "Sexo", example = "MALE") Sex sex,
        @Schema(description = "Teléfono", example = "0987654321") String phone,
        @Schema(description = "Correo electrónico", example = "juan.perez@correo.ec") String email,
        @Schema(description = "Dirección estructurada (MEJ-03)") AddressResponse address,
        @Schema(description = "Ocupación", example = "Ingeniero civil") String occupation,
        @Schema(description = "Nivel de instrucción", example = "Superior") String educationLevel,
        @Schema(description = "¿Tiene seguro de salud?", example = "true") boolean hasHealthInsurance,
        @Schema(description = "Autoidentificación étnica; null si no se indicó", example = "Mestizo")
        String ethnicSelfIdentification,
        @Schema(description = "Religión; null si no se indicó", example = "Católica") String religion,
        @Schema(description = "Datos por completar: dirección registrada antes de MEJ-03 o teléfono fuera de la regla",
                example = "false") boolean needsCompletion,
        @Schema(description = "Fecha y hora de registro") Instant createdAt,
        @Schema(description = "Fecha y hora de la última actualización; null si nunca se actualizó")
        Instant updatedAt) {

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "PatientResponse[id=" + id + "]";
    }

}
