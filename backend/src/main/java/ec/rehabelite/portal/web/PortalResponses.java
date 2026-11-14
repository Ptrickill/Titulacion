package ec.rehabelite.portal.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** Response bodies of the patient portal API. */
final class PortalResponses {

    private PortalResponses() {
    }

    @Schema(description = "Diagnóstico CIE-10")
    record DiagnosisResponse(@Schema(example = "M51.2") String code, String description) {
    }

    @Schema(description = "Tratamiento del paciente autenticado")
    record PortalTreatmentResponse(
            Long id,
            DiagnosisResponse diagnosis,
            @Schema(allowableValues = {"ACTIVE", "COMPLETED", "ABANDONED"}) String status,
            @Schema(description = "Si el tratamiento está activo") boolean active,
            @Schema(description = "Fecha de inicio: la de la evaluación inicial") LocalDate startDate,
            @Schema(description = "Nombre del fisioterapeuta responsable vigente; null si no tiene")
            String responsibleName,
            @Schema(description = "Sesiones del plan terapéutico") int plannedSessions,
            @Schema(description = "Sesiones realizadas (COMPLETED)") long completedSessions) {
    }

    @Schema(description = "Punto de la evolución del dolor")
    record PainPointResponse(
            @Schema(example = "Sesión 3", description = "\"Evaluación inicial\" o \"Sesión N\"") String label,
            LocalDate date,
            @Schema(description = "Escala EVA, 0 a 10") int painScore,
            @Schema(description = "Correlativo de la sesión; null en la evaluación inicial") Integer sequenceNumber) {
    }

    @Schema(description = "Avance de un tratamiento del paciente autenticado")
    record TreatmentProgressResponse(
            Long treatmentId,
            @Schema(description = "Sesiones realizadas (COMPLETED); las reprogramadas y las inasistencias no cuentan")
            int completedSessions,
            @Schema(description = "Sesiones del plan terapéutico") int plannedSessions,
            @Schema(description = "Realizadas sobre planificadas, redondeado hacia abajo y con tope de 100",
                    minimum = "0", maximum = "100")
            int percentage,
            @Schema(description = "EVA de la evaluación inicial, 0 a 10") int initialPainScore,
            @Schema(description = "EVA de la última sesión realizada; null si aún no hay ninguna")
            Integer lastPainScore,
            @Schema(description = "EVA inicial menos la última, en puntos: positiva si el dolor bajó, negativa si "
                    + "subió; null si aún no hay sesiones realizadas")
            Integer painReduction,
            @Schema(description = "Evaluación inicial seguida de las sesiones COMPLETED en orden cronológico; las "
                    + "sesiones RESCHEDULED y NO_SHOW no son puntos de la curva")
            List<PainPointResponse> painCurve) {
    }

}
