package ec.rehabelite.patients.web;

import ec.rehabelite.patients.application.DataSubjectRequestService;
import ec.rehabelite.patients.web.DataRequestResponses.ChangeDataRequestStatus;
import ec.rehabelite.patients.web.DataRequestResponses.DataRequestResponse;
import ec.rehabelite.patients.web.DataRequestResponses.RegisterDataRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/patients/{patientId}/data-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Solicitudes del titular", description = "Derechos del titular de los datos (MEJ-06). Solo recepción.")
class PatientDataRequestController {

    private final DataSubjectRequestService requestService;

    @GetMapping
    @Operation(summary = "Solicitudes del paciente",
            description = "Del recibido más reciente al más antiguo, cada una con su fecha límite de respuesta "
                    + "(app.compliance.request-response-days, plazo a verificar por la clínica) y los días "
                    + "transcurridos. No se audita.")
    @ApiResponse(responseCode = "200", description = "Solicitudes del paciente")
    @ApiResponse(responseCode = "404", description = "El paciente no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    List<DataRequestResponse> list(@PathVariable Long patientId) {
        return requestService.list(patientId).stream().map(DataRequestResponse::of).toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar una solicitud",
            description = "Acceso, rectificación, actualización, oposición, eliminación o portabilidad, recibida hoy o "
                    + "antes. Queda auditada como CREATE DataSubjectRequest.")
    @ApiResponse(responseCode = "201", description = "Solicitud registrada")
    @ApiResponse(responseCode = "400", description = "Datos no válidos o fecha posterior a hoy", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El paciente no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<DataRequestResponse> register(@PathVariable Long patientId,
            @Valid @RequestBody RegisterDataRequest request) {
        DataRequestResponse created = DataRequestResponse.of(requestService.register(patientId, request.type(),
                request.receivedOn()));
        return ResponseEntity.created(URI.create("/api/patients/" + patientId + "/data-requests/" + created.id()))
                .body(created);
    }

    @PatchMapping(path = "/{requestId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cambiar el estado de una solicitud",
            description = "Recibida → en curso, y abierta → resuelta o rechazada con la respuesta al titular. Una "
                    + "solicitud de eliminación solo la acepta la dirección operativa (la recepción puede rechazarla). "
                    + "Queda auditado como STATUS_CHANGE DataSubjectRequest.")
    @ApiResponse(responseCode = "200", description = "Solicitud actualizada")
    @ApiResponse(responseCode = "404", description = "El paciente o la solicitud no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "La solicitud ya está cerrada, falta la respuesta o es una "
            + "eliminación que se intenta resolver", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    DataRequestResponse changeStatus(@PathVariable Long patientId, @PathVariable Long requestId,
            @Valid @RequestBody ChangeDataRequestStatus request) {
        return DataRequestResponse.of(requestService.changeStatus(patientId, requestId, request.status(),
                request.response()));
    }

}
