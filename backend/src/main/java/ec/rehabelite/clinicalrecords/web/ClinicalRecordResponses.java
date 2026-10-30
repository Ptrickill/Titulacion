package ec.rehabelite.clinicalrecords.web;

import ec.rehabelite.clinicalrecords.domain.Severity;
import ec.rehabelite.clinicalrecords.domain.TreatmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response bodies of the clinicalrecords API. */
final class ClinicalRecordResponses {

    private ClinicalRecordResponses() {
    }

    @Schema(description = "Diagnóstico del catálogo CIE-10")
    record Icd10DiagnosisResponse(
            @Schema(example = "M54.5") String code,
            @Schema(example = "Lumbago no especificado") String description) {
    }

    @Schema(description = "Tratamiento: información diagnóstica, evaluación del dolor, plan y responsable vigente")
    record TreatmentResponse(
            Long id,
            Long clinicalRecordId,
            Icd10DiagnosisResponse diagnosis,
            String chiefComplaint,
            String currentIllness,
            String referringPhysician,
            String complementaryTests,
            Severity severity,
            String painType,
            String painLocation,
            @Schema(description = "EVA inicial, 0 a 10") int initialPainScore,
            String painFrequency,
            String sessionFrequency,
            String goal,
            int plannedSessions,
            TreatmentStatus status,
            LocalDate startDate,
            @Schema(description = "Id del fisioterapeuta responsable vigente") Long responsiblePhysiotherapistId,
            Instant createdAt) {

        @Override
        public String toString() {
            return "TreatmentResponse[id=" + id + "]";
        }

    }

    @Schema(description = "Historial clínico con sus tratamientos")
    record ClinicalRecordResponse(
            Long id,
            Long patientId,
            String habits,
            String physicalActivity,
            String allergies,
            String currentMedication,
            String personalHistory,
            String surgicalHistory,
            String familyHistory,
            Instant createdAt,
            List<TreatmentResponse> treatments) {

        @Override
        public String toString() {
            return "ClinicalRecordResponse[id=" + id + "]";
        }

    }

    @Schema(description = "Resumen del tratamiento vigente: el activo o, si no hay, el más reciente")
    record CurrentTreatmentResponse(
            Long id,
            @Schema(example = "M54.5") String diagnosisCode,
            @Schema(example = "Lumbago no especificado") String diagnosisDescription,
            Severity severity,
            int plannedSessions,
            TreatmentStatus status) {
    }

    @Schema(description = "Paciente con el resumen de su tratamiento, para la lista del fisioterapeuta")
    record PhysioPatientResponse(
            Long id,
            String nationalId,
            String medicalRecordNumber,
            String firstName,
            String lastName,
            @Schema(description = "Edad calculada desde la fecha de nacimiento") int age,
            @Schema(description = "Id del historial clínico; null si aún no tiene") Long clinicalRecordId,
            @Schema(description = "Tratamiento vigente; null sin historial o sin tratamientos")
            CurrentTreatmentResponse currentTreatment) {

        @Override
        public String toString() {
            return "PhysioPatientResponse[id=" + id + "]";
        }

    }

}
