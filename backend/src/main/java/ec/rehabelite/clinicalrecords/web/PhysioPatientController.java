package ec.rehabelite.clinicalrecords.web;

import ec.rehabelite.clinicalrecords.application.PhysioPatientService;
import ec.rehabelite.clinicalrecords.web.ClinicalRecordResponses.PhysioPatientResponse;
import ec.rehabelite.shared.domain.ClinicCalendar;
import ec.rehabelite.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/physio/patients", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Historial clínico", description = "Historial clínico, tratamientos y responsables (solo fisioterapeuta)")
class PhysioPatientController {

    private final PhysioPatientService physioPatientService;
    private final ClinicalRecordWebMapper mapper;
    private final Clock clock;

    @GetMapping
    @Operation(summary = "Pacientes del fisioterapeuta",
            description = "Pacientes paginados con el resumen de su tratamiento vigente (el activo o, si no hay, el "
                    + "más reciente): diagnóstico, severidad, sesiones planificadas y estado. La búsqueda es la de "
                    + "pacientes: cédula por inicio o nombres sin distinguir tildes. Ordenado por apellidos.")
    @ApiResponse(responseCode = "200", description = "Página de pacientes con su resumen clínico")
    PageResponse<PhysioPatientResponse> search(
            @Parameter(description = "Nombres, apellidos o inicio de la cédula", example = "perez")
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @Parameter(description = "Número de página, desde 0", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamaño de página (1 a 50)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        LocalDate today = ClinicCalendar.today(clock);
        return PageResponse.of(physioPatientService.search(q, PageRequest.of(page, size)),
                row -> mapper.toResponse(row, today));
    }

}
