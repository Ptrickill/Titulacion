package ec.rehabelite.clinicalrecords.web;

import ec.rehabelite.clinicalrecords.domain.Severity;
import ec.rehabelite.clinicalrecords.domain.TreatmentData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Diagnostic information, initial pain assessment and therapy plan of a treatment. */
@Schema(description = "Información diagnóstica, evaluación inicial del dolor y plan terapéutico")
record TreatmentRequest(
        @Schema(description = "Código CIE-10 del catálogo", example = "M54.5")
        @NotBlank @Size(max = 7) String diagnosisCode,

        @Schema(description = "Motivo de consulta", example = "Dolor lumbar al levantar peso")
        @NotBlank @Size(max = TEXT_MAX) String chiefComplaint,

        @Schema(description = "Enfermedad actual", example = "Dolor de tres semanas de evolución, sin irradiación")
        @NotBlank @Size(max = TEXT_MAX) String currentIllness,

        @Schema(description = "Médico remitente", example = "Dr. Carlos Andrade")
        @NotBlank @Size(max = 150) String referringPhysician,

        @Schema(description = "Exámenes complementarios", example = "Radiografía de columna lumbar")
        @NotBlank @Size(max = TEXT_MAX) String complementaryTests,

        @Schema(description = "Severidad", example = "MODERATE")
        @NotNull Severity severity,

        @Schema(description = "Tipo de dolor", example = "Punzante")
        @NotBlank @Size(max = 50) String painType,

        @Schema(description = "Zona del dolor", example = "Región lumbar")
        @NotBlank @Size(max = 100) String painLocation,

        @Schema(description = "Escala EVA inicial, de 0 a 10", example = "7")
        @NotNull @Min(0) @Max(10) Integer initialPainScore,

        @Schema(description = "Frecuencia del dolor", example = "Intermitente")
        @NotBlank @Size(max = 50) String painFrequency,

        @Schema(description = "Frecuencia de las sesiones", example = "3 veces por semana")
        @NotBlank @Size(max = 50) String sessionFrequency,

        @Schema(description = "Objetivo de la fisioterapia", example = "Disminuir el dolor y recuperar la movilidad")
        @NotBlank @Size(max = TEXT_MAX) String goal,

        @Schema(description = "Sesiones planificadas, mayor a 0", example = "12")
        @NotNull @Positive Integer plannedSessions) {

    /** Limit for the free-text (text) columns, so a request cannot carry arbitrarily long content. */
    static final int TEXT_MAX = 4000;

    TreatmentData toData() {
        return new TreatmentData(chiefComplaint, currentIllness, referringPhysician, complementaryTests, severity,
                painType, painLocation, initialPainScore, painFrequency, sessionFrequency, goal, plannedSessions);
    }

    /** Never print clinical data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "TreatmentRequest[...]";
    }

}
