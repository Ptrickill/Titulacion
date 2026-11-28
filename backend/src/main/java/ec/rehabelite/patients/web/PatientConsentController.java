package ec.rehabelite.patients.web;

import ec.rehabelite.patients.ConsentType;
import ec.rehabelite.patients.application.ConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping(path = "/api/patients/{patientId}/consents", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Consentimientos", description = "Consentimientos de los pacientes (MEJ-06). Solo recepción.")
class PatientConsentController {

    private final ConsentService consentService;

    @GetMapping
    @Operation(summary = "Consentimientos del paciente",
            description = "Estado de cada consentimiento y su historial. Un paciente registrado antes de MEJ-06 queda "
                    + "con DATA_PROCESSING pendiente. No se audita: no son datos clínicos.")
    @ApiResponse(responseCode = "200", description = "Consentimientos del paciente")
    @ApiResponse(responseCode = "404", description = "El paciente no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    PatientConsentsResponse list(@PathVariable Long patientId) {
        return response(patientId);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar un consentimiento firmado",
            description = "La recepción registra un consentimiento firmado en papel (PHYSICAL_SIGNATURE) para la "
                    + "versión vigente del texto; una versión anterior queda reemplazada. Queda auditado como CREATE "
                    + "PatientConsent.")
    @ApiResponse(responseCode = "200", description = "Consentimiento registrado")
    @ApiResponse(responseCode = "404", description = "El paciente no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "La versión no es la vigente, el consentimiento ya está vigente o "
            + "el paciente está bloqueado", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    PatientConsentsResponse grant(@PathVariable Long patientId, @Valid @RequestBody GrantConsentRequest request) {
        consentService.grantFromRecord(patientId, request.type(), request.version());
        return response(patientId);
    }

    @PostMapping("/{type}/revocation")
    @Operation(summary = "Revocar un consentimiento",
            description = "La recepción registra la revocación. Revocar DATA_PROCESSING desactiva el acceso al portal "
                    + "y bloquea historias clínicas, tratamientos y sesiones nuevas; lo ya registrado se conserva. Queda "
                    + "auditado como STATUS_CHANGE PatientConsent.")
    @ApiResponse(responseCode = "200", description = "Consentimiento revocado")
    @ApiResponse(responseCode = "404", description = "El paciente no existe", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "El consentimiento no está vigente", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    PatientConsentsResponse revoke(@PathVariable Long patientId, @PathVariable ConsentType type) {
        consentService.revokeFromRecord(patientId, type);
        return response(patientId);
    }

    private PatientConsentsResponse response(Long patientId) {
        return new PatientConsentsResponse(consentService.consentsOf(patientId),
                consentService.historyOf(patientId).stream().map(ConsentHistoryResponse::of).toList());
    }

}
