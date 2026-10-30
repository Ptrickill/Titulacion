package ec.rehabelite.patients.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Search result row: identification and contact data only, never clinical data. */
@Schema(description = "Paciente en los resultados de búsqueda")
record PatientSummaryResponse(
        @Schema(example = "15") Long id,
        @Schema(description = "Cédula", example = "1710034065") String nationalId,
        @Schema(description = "Número de historia clínica", example = "1710034065") String medicalRecordNumber,
        @Schema(description = "Nombres", example = "Juan Carlos") String firstName,
        @Schema(description = "Apellidos", example = "Pérez Gómez") String lastName,
        @Schema(description = "Edad en años cumplidos, calculada desde la fecha de nacimiento", example = "30")
        int age,
        @Schema(description = "Teléfono", example = "+593 98 765 4321") String phone,
        @Schema(description = "Correo electrónico", example = "juan.perez@correo.ec") String email) {

    /** Never print personal data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "PatientSummaryResponse[id=" + id + "]";
    }

}
