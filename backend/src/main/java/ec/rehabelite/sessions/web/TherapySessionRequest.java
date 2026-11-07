package ec.rehabelite.sessions.web;

import ec.rehabelite.sessions.domain.ProgressStatus;
import ec.rehabelite.sessions.domain.SessionStatus;
import ec.rehabelite.sessions.domain.TherapySessionData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The fields of a therapy session the physiotherapist writes, to register or correct it. There is no responsible
 * nor sequence number: the server assigns both, and any such property sent by the client is ignored.
 */
@Schema(description = "Datos de la sesión terapéutica. El correlativo y el responsable los asigna el servidor.")
record TherapySessionRequest(
        @Schema(description = "Fecha de la sesión, no posterior a hoy", example = "2026-11-05")
        @NotNull @PastOrPresent LocalDate sessionDate,

        @Schema(description = "Hora de la sesión", type = "string", example = "09:30")
        @NotNull LocalTime sessionTime,

        @Schema(description = "Tipo de sesión o intervención", example = "Fisioterapia lumbar mecánica")
        @NotBlank @Size(max = 50) String sessionType,

        @Schema(description = "Actividades realizadas", example = "Movilización lumbar, TENS 15 minutos")
        @NotBlank @Size(max = TEXT_MAX) String activitiesPerformed,

        @Schema(description = "Observaciones clínicas (uso interno)", example = "Buena tolerancia al ejercicio")
        @NotBlank @Size(max = TEXT_MAX) String clinicalObservations,

        @Schema(description = "Indicaciones para el paciente (opcional)", example = "Calor local 15 minutos en casa")
        @Size(max = TEXT_MAX) String patientInstructions,

        @Schema(description = "Escala EVA, de 0 a 10", example = "4")
        @NotNull @Min(0) @Max(10) Integer painScore,

        @Schema(description = "Estado de evolución", example = "IMPROVING")
        @NotNull ProgressStatus progressStatus,

        @Schema(description = "Estado de la sesión", example = "COMPLETED")
        @NotNull SessionStatus status) {

    /** Limit for the free-text (text) columns, so a request cannot carry arbitrarily long content. */
    static final int TEXT_MAX = 4000;

    TherapySessionData toData() {
        return new TherapySessionData(sessionDate, sessionTime, sessionType, activitiesPerformed,
                clinicalObservations, patientInstructions, painScore, progressStatus, status);
    }

    /** Never print clinical data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "TherapySessionRequest[...]";
    }

}
