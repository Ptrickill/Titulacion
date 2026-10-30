package ec.rehabelite.clinicalrecords.web;

import ec.rehabelite.clinicalrecords.application.ClinicalRecordService;
import ec.rehabelite.clinicalrecords.domain.ClinicalRecord;
import ec.rehabelite.clinicalrecords.domain.Treatment;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.ClinicalRecordResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.TreatmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Historial clínico", description = "Historial clínico, tratamientos y responsables (solo fisioterapeuta)")
class ClinicalRecordController {

    private final ClinicalRecordService clinicalRecordService;
    private final ClinicalRecordWebMapper mapper;

    @PostMapping(path = "/api/patients/{patientId}/clinical-record", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Crear historial clínico",
            description = "Crea el historial (hábitos y antecedentes) con su primer tratamiento en estado ACTIVE: "
                    + "información diagnóstica con diagnóstico del catálogo CIE-10, evaluación del dolor (EVA 0 a 10) "
                    + "y plan terapéutico (sesiones planificadas mayores a 0). El fisioterapeuta autenticado queda como "
                    + "responsable vigente. Queda auditado como CREATE.")
    @ApiResponse(responseCode = "201", description = "Historial y primer tratamiento creados")
    @ApiResponse(responseCode = "400", description = "Datos no válidos (p. ej. EVA fuera de 0 a 10 o sesiones en 0)",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El paciente no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "El paciente ya tiene historial; la propiedad "
            + "existingClinicalRecordId indica cuál", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "El diagnóstico no existe en el catálogo CIE-10",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<ClinicalRecordResponse> create(@PathVariable Long patientId,
            @Valid @RequestBody CreateClinicalRecordRequest request) {
        ClinicalRecord record = clinicalRecordService.create(patientId, request.toHistory(),
                request.treatment().diagnosisCode(), request.treatment().toData());
        return ResponseEntity.created(URI.create("/api/clinical-records/" + record.getId()))
                .body(mapper.toResponse(record));
    }

    @PostMapping(path = "/api/clinical-records/{clinicalRecordId}/treatments",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Abrir un nuevo tratamiento",
            description = "Abre un tratamiento ACTIVE sobre un historial existente, con el fisioterapeuta "
                    + "autenticado como responsable vigente. Un historial tiene a lo sumo un tratamiento activo. Queda "
                    + "auditado como CREATE.")
    @ApiResponse(responseCode = "201", description = "Tratamiento creado")
    @ApiResponse(responseCode = "400", description = "Datos no válidos", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El historial clínico no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Ya hay un tratamiento activo o el diagnóstico no existe en el "
            + "catálogo", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<TreatmentResponse> openTreatment(@PathVariable Long clinicalRecordId,
            @Valid @RequestBody TreatmentRequest request) {
        Treatment treatment = clinicalRecordService.openTreatment(clinicalRecordId, request.diagnosisCode(),
                request.toData());
        return ResponseEntity.created(URI.create("/api/treatments/" + treatment.getId()))
                .body(mapper.toResponse(treatment));
    }

}
