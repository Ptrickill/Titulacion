package ec.rehabelite.clinicalrecords.web;

import ec.rehabelite.clinicalrecords.application.Icd10CatalogService;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.Icd10DiagnosisResponse;
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
@RequestMapping(path = "/api/icd10-diagnoses", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Catálogo CIE-10", description = "Diagnósticos CIE-10 para los tratamientos (solo fisioterapeuta)")
class Icd10DiagnosisController {

    private final Icd10CatalogService catalogService;
    private final ClinicalRecordWebMapper mapper;

    @GetMapping
    @Operation(summary = "Buscar diagnósticos CIE-10",
            description = "Hasta 20 diagnósticos cuyo código empieza por la búsqueda o cuya descripción la contiene, "
                    + "sin distinguir tildes ni mayúsculas. Sin búsqueda lista el inicio del catálogo.")
    @ApiResponse(responseCode = "200", description = "Diagnósticos del catálogo, ordenados por código")
    List<Icd10DiagnosisResponse> search(
            @Parameter(description = "Inicio del código o parte de la descripción", example = "lumbago")
            @RequestParam(defaultValue = "") @Size(max = 100) String q) {
        return mapper.toDiagnosisResponses(catalogService.search(q));
    }

}
