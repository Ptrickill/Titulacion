package ec.rehabelite.clinicalrecords.web;

import ec.rehabelite.clinicalrecords.domain.TreatmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Cambio de estado del tratamiento con su motivo")
record ChangeTreatmentStatusRequest(
        @Schema(description = "ACTIVE → COMPLETED o ABANDONED; ABANDONED → ACTIVE", example = "COMPLETED")
        @NotNull TreatmentStatus newStatus,
        @Schema(example = "Cumplió los objetivos del plan terapéutico")
        @NotBlank @Size(max = REASON_MAX) String reason) {

    static final int REASON_MAX = 1000;

    @Override
    public String toString() {
        return "ChangeTreatmentStatusRequest[newStatus=" + newStatus + "]";
    }

}
