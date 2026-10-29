package ec.rehabelite.patients.web;

import ec.rehabelite.patients.application.PortalAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/patients", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Pacientes", description = "Datos personales de los pacientes")
class PatientController {

    private final PortalAccountService portalAccountService;
    private final PatientWebMapper mapper;

    @GetMapping("/portal-candidates")
    @Operation(summary = "Pacientes sin cuenta de portal",
            description = "Hasta 20 pacientes sin cuenta de portal cuyo nombre (sin distinguir tildes ni "
                    + "mayúsculas) o cédula coinciden con la búsqueda. Solo administrador.")
    @ApiResponse(responseCode = "200", description = "Pacientes que pueden recibir una cuenta de portal")
    List<PortalCandidateResponse> findPortalCandidates(
            @Parameter(description = "Nombre, apellido o inicio de la cédula", example = "ortiz")
            @RequestParam(defaultValue = "") @Size(max = 100) String query) {
        return mapper.toPortalCandidates(portalAccountService.findCandidates(query));
    }

}
