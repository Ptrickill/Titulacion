package ec.rehabelite.clinicalrecords.web;

import ec.rehabelite.clinicalrecords.application.TreatmentService;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.PhysiotherapistResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.TreatmentHistoryResponse;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.TreatmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Historial clínico", description = "Historial clínico, tratamientos y responsables (solo fisioterapeuta)")
class TreatmentController {

    private final TreatmentService treatmentService;
    private final ClinicalRecordWebMapper mapper;

    @GetMapping(path = "/api/treatments/{treatmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar un tratamiento",
            description = "Información diagnóstica, evaluación del dolor, plan terapéutico, estado y responsable "
                    + "vigente del tratamiento.")
    @ApiResponse(responseCode = "200", description = "Tratamiento")
    @ApiResponse(responseCode = "404", description = "El tratamiento no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    TreatmentResponse find(@PathVariable Long treatmentId) {
        return mapper.toResponse(treatmentService.find(treatmentId));
    }

    @GetMapping(path = "/api/treatments/{treatmentId}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Historial de responsables y de estados",
            description = "Estado actual, estados a los que puede pasar, responsable vigente, responsables "
                    + "anteriores con su periodo y cambios de estado con motivo, usuario y fecha, del más reciente al "
                    + "más antiguo.")
    @ApiResponse(responseCode = "200", description = "Trazabilidad del tratamiento")
    @ApiResponse(responseCode = "404", description = "El tratamiento no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    TreatmentHistoryResponse history(@PathVariable Long treatmentId) {
        return mapper.toResponse(treatmentService.history(treatmentId));
    }

    @PutMapping(path = "/api/treatments/{treatmentId}/responsible", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cambiar el fisioterapeuta responsable",
            description = "Cierra la asignación vigente con la fecha actual y abre una nueva para el fisioterapeuta "
                    + "indicado, que debe ser un usuario activo con rol PHYSIOTHERAPIST. Solo en tratamientos ACTIVE. "
                    + "Elegir al responsable vigente no cambia nada. Cada cambio queda auditado como UPDATE.")
    @ApiResponse(responseCode = "200", description = "Trazabilidad actualizada del tratamiento")
    @ApiResponse(responseCode = "400", description = "Falta el fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El tratamiento no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "El usuario no es un fisioterapeuta activo o el tratamiento no "
            + "está activo", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    TreatmentHistoryResponse changeResponsible(@PathVariable Long treatmentId,
            @Valid @RequestBody ChangeResponsibleRequest request) {
        return mapper.toResponse(treatmentService.changeResponsible(treatmentId, request.physiotherapistId()));
    }

    @PatchMapping(path = "/api/treatments/{treatmentId}/status", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cambiar el estado del tratamiento",
            description = "Transiciones permitidas: ACTIVE → COMPLETED, ACTIVE → ABANDONED y ABANDONED → ACTIVE "
                    + "(solo si el historial no tiene otro tratamiento activo). COMPLETED es un estado final. Guarda "
                    + "el estado anterior, el nuevo, el motivo, el usuario y la fecha, y queda auditado como "
                    + "STATUS_CHANGE.")
    @ApiResponse(responseCode = "200", description = "Trazabilidad actualizada del tratamiento")
    @ApiResponse(responseCode = "400", description = "Falta el estado o el motivo, o el motivo es demasiado largo",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es fisioterapeuta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El tratamiento no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Transición de estado no permitida, o el historial ya tiene "
            + "otro tratamiento activo", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    TreatmentHistoryResponse changeStatus(@PathVariable Long treatmentId,
            @Valid @RequestBody ChangeTreatmentStatusRequest request) {
        return mapper.toResponse(treatmentService.changeStatus(treatmentId, request.newStatus(), request.reason()));
    }

    @GetMapping(path = "/api/physio/physiotherapists", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Fisioterapeutas activos",
            description = "Fisioterapeutas que pueden ser responsables de un tratamiento, ordenados por nombre.")
    @ApiResponse(responseCode = "200", description = "Fisioterapeutas activos")
    List<PhysiotherapistResponse> physiotherapists() {
        return mapper.toPhysiotherapistResponses(treatmentService.physiotherapists());
    }

}
