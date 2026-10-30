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
            Long patientId,
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

    @Schema(description = "Hábitos y antecedentes del historial clínico")
    record ClinicalHistoryResponse(
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
            @Schema(description = "Última edición de hábitos y antecedentes; null si nunca se editó")
            Instant updatedAt) {

        @Override
        public String toString() {
            return "ClinicalHistoryResponse[id=" + id + "]";
        }

    }

    @Schema(description = "Datos personales y de contacto del paciente")
    record PatientProfileResponse(
            Long id,
            String nationalId,
            String medicalRecordNumber,
            String firstName,
            String lastName,
            LocalDate birthDate,
            @Schema(description = "Edad calculada desde la fecha de nacimiento") int age,
            @Schema(allowableValues = {"MALE", "FEMALE"}) String sex,
            String phone,
            String email,
            String address,
            String occupation,
            String educationLevel,
            String healthInsurance,
            LocalDate admissionDate) {

        @Override
        public String toString() {
            return "PatientProfileResponse[id=" + id + "]";
        }

    }

    @Schema(description = "Tratamiento activo: información diagnóstica, evaluación del dolor, plan y responsable vigente")
    record ActiveTreatmentResponse(
            Long id,
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
            @Schema(description = "Responsable vigente; null si no hay") ResponsibleResponse currentResponsible) {

        @Override
        public String toString() {
            return "ActiveTreatmentResponse[id=" + id + "]";
        }

    }

    @Schema(description = "Tratamiento anterior (finalizado o abandonado)")
    record PreviousTreatmentResponse(
            Long id,
            Icd10DiagnosisResponse diagnosis,
            Severity severity,
            TreatmentStatus status,
            LocalDate startDate,
            int plannedSessions,
            @Schema(description = "Último cambio del tratamiento (p. ej. su cierre); null si no cambió")
            Instant updatedAt,
            @Schema(description = "Último responsable; null si no hay") ResponsibleResponse responsible) {
    }

    @Schema(description = "Expediente completo del paciente: datos personales, hábitos y antecedentes, tratamiento "
            + "activo con su responsable y tratamientos anteriores (del más reciente al más antiguo)")
    record ClinicalRecordViewResponse(
            PatientProfileResponse patient,
            ClinicalHistoryResponse clinicalRecord,
            @Schema(description = "Tratamiento activo; null si no hay") ActiveTreatmentResponse activeTreatment,
            List<PreviousTreatmentResponse> previousTreatments) {

        @Override
        public String toString() {
            return "ClinicalRecordViewResponse[clinicalRecordId=" + clinicalRecord.id() + "]";
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

    @Schema(description = "Periodo de un fisioterapeuta como responsable del tratamiento")
    record ResponsibleResponse(
            Long physiotherapistId,
            @Schema(example = "Lcda. Valeria Andrade") String fullName,
            Instant validFrom,
            @Schema(description = "Fin del periodo; null en el responsable vigente") Instant validTo) {
    }

    @Schema(description = "Cambio de estado del tratamiento con su motivo, usuario y fecha")
    record StatusChangeResponse(
            Long id,
            TreatmentStatus previousStatus,
            TreatmentStatus newStatus,
            String reason,
            @Schema(description = "Id del usuario que hizo el cambio") Long changedById,
            String changedByName,
            Instant changedAt) {

        @Override
        public String toString() {
            return "StatusChangeResponse[id=" + id + "]";
        }

    }

    @Schema(description = "Trazabilidad del tratamiento: estado, transiciones permitidas, responsables y cambios de "
            + "estado (del más reciente al más antiguo)")
    record TreatmentHistoryResponse(
            Long treatmentId,
            TreatmentStatus status,
            @Schema(description = "Estados a los que puede pasar ahora: ACTIVE → COMPLETED o ABANDONED; "
                    + "ABANDONED → ACTIVE; COMPLETED es final") List<TreatmentStatus> allowedNextStatuses,
            @Schema(description = "Responsable vigente; null si no hay") ResponsibleResponse currentResponsible,
            List<ResponsibleResponse> responsibles,
            List<StatusChangeResponse> statusChanges) {

        @Override
        public String toString() {
            return "TreatmentHistoryResponse[treatmentId=" + treatmentId + "]";
        }

    }

    @Schema(description = "Fisioterapeuta activo que puede ser responsable de un tratamiento")
    record PhysiotherapistResponse(
            Long id,
            @Schema(example = "Lcda. Valeria Andrade") String fullName) {
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
