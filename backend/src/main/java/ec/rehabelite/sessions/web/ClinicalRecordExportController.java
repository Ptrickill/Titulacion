package ec.rehabelite.sessions.web;

import ec.rehabelite.sessions.application.ClinicalRecordExporter;
import ec.rehabelite.sessions.application.ClinicalRecordExporter.ClinicalRecordExport;
import ec.rehabelite.shared.domain.ClinicCalendar;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Export of the clinical record (MEJ-06): the physiotherapist hands it to the data subject from the record view.
 * Under /api/patients/{id}/clinical-record, the record's path, although the sessions module answers it: only this
 * module reads the internal session notes the record includes.
 */
@RestController
@RequestMapping("/api/patients/{patientId}/clinical-record")
@RequiredArgsConstructor
@Tag(name = "Historia clínica", description = "Exportación de la historia clínica (MEJ-06)")
class ClinicalRecordExportController {

    private final ClinicalRecordExporter exporter;
    private final Clock clock;

    @GetMapping(path = "/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Exportar la historia clínica en PDF",
            description = "La historia clínica completa del paciente: identificación, hábitos y antecedentes, y cada "
                    + "tratamiento con su plan, responsables, cambios de estado y sesiones (también las observaciones "
                    + "clínicas). Para entregarla al titular (derechos de acceso y portabilidad). Queda auditado como "
                    + "EXPORT ClinicalRecord y se envía con Cache-Control: no-store. Solo fisioterapeuta.")
    @ApiResponse(responseCode = "200", description = "PDF de la historia clínica")
    @ApiResponse(responseCode = "404", description = "El paciente no existe o no tiene historial clínico",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<byte[]> exportPdf(@PathVariable Long patientId) {
        return file(exporter.exportPdf(patientId), MediaType.APPLICATION_PDF, patientId, "pdf");
    }

    @GetMapping(path = "/export.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Exportar la historia clínica en JSON",
            description = "El mismo contenido que el PDF, en un formato estructurado (portabilidad). Queda auditado como "
                    + "EXPORT ClinicalRecord y se envía con Cache-Control: no-store. Solo fisioterapeuta.")
    @ApiResponse(responseCode = "200", description = "JSON de la historia clínica")
    @ApiResponse(responseCode = "404", description = "El paciente no existe o no tiene historial clínico",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<ClinicalRecordExport> exportJson(@PathVariable Long patientId) {
        return file(exporter.export(patientId), MediaType.APPLICATION_JSON, patientId, "json");
    }

    private <T> ResponseEntity<T> file(T body, MediaType type, Long patientId, String extension) {
        String name = "historia-clinica-" + patientId + "-" + ClinicCalendar.today(clock) + "." + extension;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build()
                        .toString())
                .body(body);
    }

}
