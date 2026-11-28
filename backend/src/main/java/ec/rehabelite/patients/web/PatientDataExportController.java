package ec.rehabelite.patients.web;

import ec.rehabelite.patients.application.PatientDataExportService;
import ec.rehabelite.patients.application.PatientDataExportService.PatientDataExport;
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

@RestController
@RequestMapping("/api/patients/{patientId}")
@RequiredArgsConstructor
@Tag(name = "Solicitudes del titular", description = "Derechos del titular de los datos (MEJ-06). Solo recepción.")
class PatientDataExportController {

    private final PatientDataExportService exportService;
    private final Clock clock;

    @GetMapping(path = "/data-export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Exportar los datos del paciente en PDF",
            description = "Datos personales y de contacto, acceso al portal, consentimientos y solicitudes del titular, "
                    + "para entregarlos al titular. Nunca datos clínicos: la historia clínica la exporta el "
                    + "fisioterapeuta. Queda auditado como EXPORT Patient y se envía con Cache-Control: no-store.")
    @ApiResponse(responseCode = "200", description = "PDF de los datos del paciente")
    @ApiResponse(responseCode = "404", description = "El paciente no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<byte[]> exportPdf(@PathVariable Long patientId) {
        return file(exportService.exportPdf(patientId), MediaType.APPLICATION_PDF, patientId, "pdf");
    }

    @GetMapping(path = "/data-export.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Exportar los datos del paciente en JSON",
            description = "El mismo contenido que el PDF, en un formato estructurado (portabilidad). Queda auditado como "
                    + "EXPORT Patient y se envía con Cache-Control: no-store.")
    @ApiResponse(responseCode = "200", description = "JSON de los datos del paciente")
    @ApiResponse(responseCode = "404", description = "El paciente no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<PatientDataExport> exportJson(@PathVariable Long patientId) {
        return file(exportService.export(patientId), MediaType.APPLICATION_JSON, patientId, "json");
    }

    private <T> ResponseEntity<T> file(T body, MediaType type, Long patientId, String extension) {
        String name = "datos-paciente-" + patientId + "-" + ClinicCalendar.today(clock) + "." + extension;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build()
                        .toString())
                .body(body);
    }

}
