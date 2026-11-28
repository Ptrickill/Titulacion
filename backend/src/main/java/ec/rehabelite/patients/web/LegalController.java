package ec.rehabelite.patients.web;

import ec.rehabelite.patients.LegalText;
import ec.rehabelite.patients.application.LegalTextCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/legal", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Textos legales", description = "Aviso de privacidad y textos de los consentimientos (MEJ-06)")
class LegalController {

    private final LegalTextCatalog catalog;

    @GetMapping("/privacy-notice")
    @Operation(summary = "Aviso de privacidad",
            description = "La versión vigente del aviso de privacidad, en Markdown. Público: lo enlazan el inicio de "
                    + "sesión y el portal del paciente.")
    @ApiResponse(responseCode = "200", description = "Aviso de privacidad vigente")
    LegalText privacyNotice() {
        return catalog.privacyNotice();
    }

    @GetMapping("/consents")
    @Operation(summary = "Textos de los consentimientos",
            description = "La versión vigente del texto de cada consentimiento (DATA_PROCESSING y "
                    + "EMAIL_COMMUNICATIONS), en Markdown. Al registrarlo se envía la versión mostrada.")
    @ApiResponse(responseCode = "200", description = "Textos vigentes, DATA_PROCESSING primero")
    List<LegalText> consents() {
        return catalog.consents();
    }

}
