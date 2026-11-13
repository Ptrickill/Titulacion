package ec.rehabelite.sessions.web;

import ec.rehabelite.audit.EntityVersion;
import ec.rehabelite.sessions.domain.ProgressStatus;
import ec.rehabelite.sessions.domain.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Response bodies of the sessions API. */
final class TherapySessionResponses {

    private TherapySessionResponses() {
    }

    @Schema(description = "Usuario del personal")
    record StaffResponse(
            Long id,
            @Schema(description = "Nombre completo; null si la cuenta ya no existe") String fullName) {
    }

    @Schema(description = "Sesión terapéutica")
    record TherapySessionResponse(
            Long id,
            Long treatmentId,
            @Schema(description = "Número correlativo de la sesión en el tratamiento, asignado por el servidor")
            int sequenceNumber,
            LocalDate sessionDate,
            @Schema(type = "string", example = "09:30:00") LocalTime sessionTime,
            String sessionType,
            String activitiesPerformed,
            @Schema(description = "Observaciones clínicas (uso interno)") String clinicalObservations,
            String patientInstructions,
            @Schema(description = "Escala EVA, 0 a 10") int painScore,
            ProgressStatus progressStatus,
            SessionStatus status,
            @Schema(description = "Fisioterapeuta que registró la sesión; nunca cambia") StaffResponse responsible,
            Instant createdAt,
            @Schema(description = "Usuario de la última corrección") Long updatedBy,
            @Schema(description = "Fecha de la última corrección") Instant updatedAt) {

        @Override
        public String toString() {
            return "TherapySessionResponse[id=" + id + "]";
        }

    }

    @Schema(description = "Diagnóstico CIE-10")
    record DiagnosisResponse(@Schema(example = "M51.2") String code, String description) {
    }

    @Schema(description = "Punto de partida y plan del tratamiento")
    record TimelineTreatmentResponse(
            Long id,
            Long patientId,
            @Schema(description = "Si el tratamiento está activo (solo entonces se registran sesiones)") boolean active,
            @Schema(description = "Fecha de inicio: la de la evaluación inicial") LocalDate startDate,
            @Schema(description = "EVA de la evaluación inicial, 0 a 10") int initialPainScore,
            @Schema(description = "Sesiones del plan terapéutico") int plannedSessions,
            @Schema(description = "Sesiones COMPLETED de todo el tratamiento, sin importar el filtro")
            long completedSessions,
            DiagnosisResponse diagnosis) {
    }

    @Schema(description = "Punto de la curva de EVA")
    record PainPointResponse(
            @Schema(example = "Sesión 3", description = "\"Evaluación inicial\" o \"Sesión N\"") String label,
            LocalDate date,
            @Schema(description = "Escala EVA, 0 a 10") int painScore,
            @Schema(description = "Sesión del punto; null en la evaluación inicial") Long sessionId,
            @Schema(description = "Correlativo de la sesión; null en la evaluación inicial") Integer sequenceNumber) {
    }

    @Schema(description = "Línea de tiempo terapéutica de un tratamiento")
    record TimelineResponse(
            TimelineTreatmentResponse treatment,
            @Schema(description = "Filtro aplicado: desde (día, inclusive); null sin filtro") LocalDate from,
            @Schema(description = "Filtro aplicado: hasta (día, inclusive); null sin filtro") LocalDate to,
            @Schema(description = "Sesiones del rango en orden cronológico (fecha, hora y correlativo)")
            List<TherapySessionResponse> sessions,
            @Schema(description = "Evaluación inicial seguida de las sesiones COMPLETED del rango; las sesiones "
                    + "RESCHEDULED y NO_SHOW no son puntos de la curva")
            List<PainPointResponse> painCurve) {
    }

    @Schema(description = "Versión de una sesión terapéutica guardada por Hibernate Envers")
    record TherapySessionVersionResponse(
            @Schema(description = "Número de revisión") long revision,
            @Schema(description = "CREATE: registro; UPDATE: corrección") EntityVersion.Type type,
            @Schema(description = "Fecha y hora de la revisión") Instant changedAt,
            @Schema(description = "Autor de la revisión; null si no hubo usuario autenticado") StaffResponse changedBy,
            @Schema(description = "Contenido de la sesión en esa versión") TherapySessionResponse session) {
    }

}
