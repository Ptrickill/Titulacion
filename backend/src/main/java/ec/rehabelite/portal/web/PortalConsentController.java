package ec.rehabelite.portal.web;

import ec.rehabelite.auth.CurrentUserProvider;
import ec.rehabelite.patients.ConsentType;
import ec.rehabelite.patients.PatientConsentView;
import ec.rehabelite.patients.PatientConsents;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

/**
 * The patient's own consents in the portal (MEJ-06), the only writes of the portal. The patient is the one linked to
 * the authenticated account: no endpoint receives a patient id.
 */
@RestController
@RequestMapping(path = "/api/portal/consents", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Portal del paciente", description = "Consultas de solo lectura del propio paciente")
class PortalConsentController {

    private final PatientConsents consents;
    private final CurrentUserProvider currentUserProvider;

    @Schema(description = "Aceptación del texto vigente de un consentimiento")
    record AcceptConsentRequest(
            @Schema(description = "Tipo de consentimiento", example = "EMAIL_COMMUNICATIONS") @NotNull ConsentType type,
            @Schema(description = "Versión vigente del texto mostrado", example = "0.1-borrador")
            @NotBlank @Size(max = 30) String version) {
    }

    @GetMapping
    @Operation(summary = "Mis consentimientos",
            description = "El estado de cada consentimiento del paciente de la cuenta. Los textos vigentes están en "
                    + "GET /api/legal/consents.")
    @ApiResponse(responseCode = "200", description = "Consentimientos del paciente, DATA_PROCESSING primero")
    @ApiResponse(responseCode = "404", description = "La cuenta no tiene un paciente vinculado", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    List<PatientConsentView> list() {
        return consents.ofPortalUser(currentUserId());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Aceptar un consentimiento",
            description = "El paciente acepta el texto vigente (PORTAL); una versión anterior queda reemplazada. Queda "
                    + "auditado como CREATE PatientConsent.")
    @ApiResponse(responseCode = "200", description = "Consentimiento aceptado")
    @ApiResponse(responseCode = "422", description = "La versión no es la vigente o el consentimiento ya está vigente",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    List<PatientConsentView> accept(@Valid @RequestBody AcceptConsentRequest request) {
        return consents.acceptFromPortal(currentUserId(), request.type(), request.version());
    }

    @PostMapping("/{type}/revocation")
    @Operation(summary = "Revocar un consentimiento",
            description = "Solo EMAIL_COMMUNICATIONS: el consentimiento del tratamiento de datos se revoca en recepción, "
                    + "porque cierra el acceso al portal. Queda auditado como STATUS_CHANGE PatientConsent.")
    @ApiResponse(responseCode = "200", description = "Consentimiento revocado")
    @ApiResponse(responseCode = "422", description = "Es DATA_PROCESSING o no está vigente", content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    List<PatientConsentView> revoke(@PathVariable ConsentType type) {
        return consents.revokeFromPortal(currentUserId(), type);
    }

    private Long currentUserId() {
        return currentUserProvider.currentUserId().orElseThrow();
    }

}
