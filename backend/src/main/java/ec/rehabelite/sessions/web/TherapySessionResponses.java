package ec.rehabelite.sessions.web;

import ec.rehabelite.audit.EntityVersion;
import ec.rehabelite.sessions.domain.ProgressStatus;
import ec.rehabelite.sessions.domain.SessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

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

    @Schema(description = "Versión de una sesión terapéutica guardada por Hibernate Envers")
    record TherapySessionVersionResponse(
            @Schema(description = "Número de revisión") long revision,
            @Schema(description = "CREATE: registro; UPDATE: corrección") EntityVersion.Type type,
            @Schema(description = "Fecha y hora de la revisión") Instant changedAt,
            @Schema(description = "Autor de la revisión; null si no hubo usuario autenticado") StaffResponse changedBy,
            @Schema(description = "Contenido de la sesión en esa versión") TherapySessionResponse session) {
    }

}
