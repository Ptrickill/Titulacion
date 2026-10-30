package ec.rehabelite.patients.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Paciente registrado que aún no tiene cuenta de portal")
record PortalCandidateResponse(
        @Schema(example = "15") Long id,
        @Schema(description = "Apellidos y nombres", example = "Ortiz Vega Rosa Elena") String fullName,
        @Schema(description = "Cédula", example = "1710034065") String nationalId,
        @Schema(description = "Número de historia clínica", example = "1710034065") String medicalRecordNumber) {
}
