package ec.rehabelite.clinicalrecords.web;

import static ec.rehabelite.clinicalrecords.web.TreatmentRequest.TEXT_MAX;

import ec.rehabelite.clinicalrecords.domain.ClinicalHistoryData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Habits and medical history of the new clinical record, plus its first treatment. */
@Schema(description = "Hábitos y antecedentes del historial clínico, con su primer tratamiento")
record CreateClinicalRecordRequest(
        @Schema(description = "Hábitos", example = "No fuma; consumo ocasional de alcohol")
        @NotBlank @Size(max = TEXT_MAX) String habits,

        @Schema(description = "Actividad física", example = "Camina 30 minutos tres veces por semana")
        @NotBlank @Size(max = TEXT_MAX) String physicalActivity,

        @Schema(description = "Alergias", example = "Ninguna conocida")
        @NotBlank @Size(max = TEXT_MAX) String allergies,

        @Schema(description = "Medicación actual", example = "Ibuprofeno 400 mg cada 8 horas")
        @NotBlank @Size(max = TEXT_MAX) String currentMedication,

        @Schema(description = "Antecedentes personales", example = "Hipertensión controlada")
        @NotBlank @Size(max = TEXT_MAX) String personalHistory,

        @Schema(description = "Antecedentes quirúrgicos", example = "Apendicectomía (2010)")
        @NotBlank @Size(max = TEXT_MAX) String surgicalHistory,

        @Schema(description = "Antecedentes familiares", example = "Madre con artrosis de rodilla")
        @NotBlank @Size(max = TEXT_MAX) String familyHistory,

        @Schema(description = "Primer tratamiento")
        @Valid @NotNull TreatmentRequest treatment) {

    ClinicalHistoryData toHistory() {
        return new ClinicalHistoryData(habits, physicalActivity, allergies, currentMedication, personalHistory,
                surgicalHistory, familyHistory);
    }

    /** Never print clinical data, e.g. in logs or debugger views. */
    @Override
    public String toString() {
        return "CreateClinicalRecordRequest[...]";
    }

}
