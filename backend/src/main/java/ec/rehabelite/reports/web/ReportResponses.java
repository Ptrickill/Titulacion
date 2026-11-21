package ec.rehabelite.reports.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Response bodies of the operations reports API. */
final class ReportResponses {

    private ReportResponses() {
    }

    @Schema(description = "Tratamientos por estado (HU-08), filtrados por estado y por responsable vigente")
    record TreatmentCountsResponse(
            @Schema(description = "Tratamientos ACTIVE") long active,
            @Schema(description = "Tratamientos COMPLETED (alta)") long completed,
            @Schema(description = "Tratamientos ABANDONED") long abandoned) {
    }

    @Schema(description = "Sesiones registradas, filtradas por quien las registró y por el estado de su tratamiento")
    record SessionCountsResponse(
            @Schema(description = "Todas las sesiones, sea cual sea su estado") long total,
            @Schema(description = "Sesiones COMPLETED") long completed,
            @Schema(description = "Sesiones RESCHEDULED") long rescheduled,
            @Schema(description = "Sesiones NO_SHOW") long noShow) {
    }

    @Schema(description = "Sesiones registradas por un fisioterapeuta, sea cual sea su estado")
    record PhysiotherapistSessionsResponse(
            Long physiotherapistId,
            @Schema(example = "Lcda. Valeria Andrade") String name,
            long sessions) {
    }

    @Schema(description = "Indicadores del reporte de dirección operativa")
    record ReportSummaryResponse(
            TreatmentCountsResponse treatments,
            SessionCountsResponse sessions,
            @Schema(description = "De más a menos sesiones; solo los fisioterapeutas con al menos una")
            List<PhysiotherapistSessionsResponse> sessionsByPhysiotherapist,
            @Schema(description = "Total de sesiones entre los fisioterapeutas con al menos una, con un decimal; 0.0 "
                    + "si no hay sesiones", example = "5.6")
            BigDecimal averageSessionsPerPhysiotherapist) {
    }

    @Schema(description = "Diagnóstico CIE-10")
    record DiagnosisResponse(@Schema(example = "M51.2") String code, String description) {
    }

    @Schema(description = "Tratamiento del reporte. Solo el nombre del paciente: sin cédula ni otros datos personales")
    record ReportTreatmentResponse(
            Long id,
            @Schema(description = "Nombres y apellidos del paciente") String patientName,
            DiagnosisResponse diagnosis,
            @Schema(description = "Fisioterapeuta responsable vigente; null si no tiene") String responsibleName,
            @Schema(allowableValues = {"ACTIVE", "COMPLETED", "ABANDONED"}) String status,
            LocalDate startDate) {

        /** Never print personal data, e.g. in logs or debugger views. */
        @Override
        public String toString() {
            return "ReportTreatmentResponse[id=" + id + "]";
        }

    }

    @Schema(description = "Fisioterapeuta para el filtro del reporte")
    record PhysiotherapistOptionResponse(Long id, @Schema(example = "Lcda. Valeria Andrade") String fullName) {
    }

}
