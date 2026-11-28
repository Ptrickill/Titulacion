package ec.rehabelite.patients.web;

import ec.rehabelite.patients.application.DataSubjectRequestService;
import ec.rehabelite.patients.web.DataRequestResponses.AcceptErasure;
import ec.rehabelite.patients.web.DataRequestResponses.DataRequestResponse;
import ec.rehabelite.patients.web.DataRequestResponses.ErasureRequestResponse;
import ec.rehabelite.patients.web.DataRequestResponses.RejectErasure;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/data-requests/erasures", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Solicitudes de eliminación", description = "Las acepta o rechaza la dirección operativa (MEJ-06)")
class ErasureRequestController {

    private final DataSubjectRequestService requestService;

    @GetMapping
    @Operation(summary = "Solicitudes de eliminación abiertas",
            description = "De la más antigua a la más reciente, con el nombre y la cédula del paciente (para "
                    + "confirmar) y su fecha límite. Solo dirección operativa. No se audita.")
    @ApiResponse(responseCode = "200", description = "Solicitudes abiertas")
    List<ErasureRequestResponse> list() {
        return requestService.openErasureRequests().stream().map(ErasureRequestResponse::of).toList();
    }

    @PostMapping(path = "/{requestId}/acceptance", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Aceptar una solicitud de eliminación",
            description = "Se confirma escribiendo la cédula del paciente. Bloquea al paciente (nadie lo edita ni le "
                    + "crea tratamientos, sesiones ni acceso al portal), borra su correo, teléfono y dirección, revoca "
                    + "sus consentimientos y desactiva y anonimiza su cuenta del portal. La historia clínica se conserva "
                    + "y sigue legible para el fisioterapeuta. Irreversible. Queda auditado como ANONYMIZATION Patient, "
                    + "ANONYMIZATION User y STATUS_CHANGE DataSubjectRequest.")
    @ApiResponse(responseCode = "200", description = "Solicitud resuelta y datos anonimizados")
    @ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "No es una eliminación abierta, la cédula no coincide o el "
            + "paciente ya está bloqueado", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    DataRequestResponse accept(@PathVariable Long requestId, @Valid @RequestBody AcceptErasure request) {
        return DataRequestResponse.of(requestService.acceptErasure(requestId, request.nationalId(),
                request.response()));
    }

    @PostMapping(path = "/{requestId}/rejection", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rechazar una solicitud de eliminación",
            description = "Cierra la solicitud con la respuesta al titular; no cambia nada del paciente. Queda auditado "
                    + "como STATUS_CHANGE DataSubjectRequest.")
    @ApiResponse(responseCode = "200", description = "Solicitud rechazada")
    @ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "No es una eliminación abierta", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    DataRequestResponse reject(@PathVariable Long requestId, @Valid @RequestBody RejectErasure request) {
        return DataRequestResponse.of(requestService.rejectErasure(requestId, request.response()));
    }

}
